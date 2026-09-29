package gov.openpto.gateway;

import gov.openpto.gateway.support.StubDownstream;
import gov.openpto.gateway.usage.UsageMeter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end through a real Tomcat: the stub plays odp-service and ingest-service; fee-service points at
 * a closed port. Each test uses its own client IP (via X-Forwarded-For from the trusted loopback peer) or
 * identity so rate-limit buckets do not interfere.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    static final StubDownstream STUB = StubDownstream.start();
    static final int CLOSED_PORT = StubDownstream.closedPort();
    static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.services.odp.url", STUB::baseUrl);
        registry.add("gateway.services.ingest.url", STUB::baseUrl);
        registry.add("gateway.services.fees.url", () -> "http://127.0.0.1:" + CLOSED_PORT);
        registry.add("gateway.services.fees.connect-timeout", () -> "5s");
        registry.add("gateway.services.odp.read-timeout", () -> "1s");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> STUB.baseUrl() + "/.well-known/jwks.json");
        registry.add("gateway.trusted-proxies", () -> "127.0.0.1,::1");
        registry.add("gateway.rate-limit.tiers.ANONYMOUS.per-minute", () -> "5");
        registry.add("gateway.usage.flush-interval-ms", () -> "3600000");
        registry.add("gateway.circuit-breaker.failure-threshold", () -> "100");
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    UsageMeter usageMeter;

    // created lazily: the JDK reads jdk.httpclient.allowRestrictedHeaders once, and the gateway sets it at startup
    HttpClient http;

    @BeforeEach
    void client() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    // ---------- routing, headers, correlation ----------

    @Test
    void dataRoute_proxiesWithRateLimitHeadersAndSanitisedForwardHeaders() throws Exception {
        HttpResponse<String> res = send(get("/api/v1/patents?q=solar&size=5")
                .header("X-Forwarded-For", "10.0.0.1")
                .header("X-Internal-Token", "stolen")
                .header("X-Request-Id", "req-test-000001"));

        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode echo = JSON.readTree(res.body());
        assertThat(echo.path("path").asString()).isEqualTo("/api/v1/patents");
        assertThat(echo.path("query").asString()).isEqualTo("q=solar&size=5");
        JsonNode headers = echo.path("headers");
        assertThat(headers.has("x-internal-token")).isFalse();
        assertThat(headers.path("x-request-id").asString()).isEqualTo("req-test-000001");
        assertThat(headers.path("x-forwarded-for").asString()).isEqualTo("10.0.0.1, 127.0.0.1");
        assertThat(headers.path("host").asString()).isEqualTo("localhost:" + port);
        assertThat(headers.path("x-forwarded-proto").asString()).isEqualTo("http");

        assertThat(res.headers().allValues("X-Request-Id")).containsExactly("req-test-000001");
        assertThat(res.headers().firstValue("X-RateLimit-Limit")).hasValue("5");
        assertThat(res.headers().firstValue("X-RateLimit-Remaining")).hasValue("4");
        assertThat(res.headers().firstValue("X-RateLimit-Reset")).isPresent();
        assertThat(res.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    }

    @Test
    void requestId_generatedWhenAbsentOrUnsafe() throws Exception {
        HttpResponse<String> res = send(get("/api/v1/stats").header("X-Forwarded-For", "10.0.0.3")
                .header("X-Request-Id", "bad id with spaces"));
        String id = res.headers().firstValue("X-Request-Id").orElseThrow();
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(JSON.readTree(res.body()).path("headers").path("x-request-id").asString()).isEqualTo(id);
    }

    @Test
    void internalPaths_areAlways404() throws Exception {
        int before = STUB.usagePosts().size();
        HttpResponse<String> res = send(HttpRequest.newBuilder(uri("/internal/v1/usage"))
                .header("X-Internal-Token", "dev-internal-token-change-me")
                .POST(HttpRequest.BodyPublishers.ofString("{\"entries\":[]}")));
        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/problem+json"));
        JsonNode problem = JSON.readTree(res.body());
        assertThat(problem.path("status").asInt()).isEqualTo(404);
        assertThat(problem.path("requestId").asString()).isNotBlank();
        assertThat(STUB.usagePosts()).hasSize(before);
    }

    @Test
    void nonNormalisedTraversal_isRejectedBeforeRouting() throws Exception {
        HttpResponse<String> res = send(get("/api/v1/patents/../../internal/v1/usage"));
        assertThat(res.statusCode()).isIn(400, 404);
    }

    @Test
    void unknownPath_is404ProblemDetail() throws Exception {
        HttpResponse<String> res = send(get("/nope/nothing-here"));
        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(res.body()).path("requestId").asString()).as(res.body()).isNotBlank();
    }

    // ---------- rate limiting ----------

    @Test
    void anonymous_exceedingLimit_gets429WithRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(send(get("/api/v1/trademarks").header("X-Forwarded-For", "10.0.0.2")).statusCode())
                    .isEqualTo(200);
        }
        HttpResponse<String> limited = send(get("/api/v1/trademarks").header("X-Forwarded-For", "10.0.0.2"));
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).hasValueSatisfying(
                v -> assertThat(Long.parseLong(v)).isPositive());
        assertThat(limited.headers().firstValue("X-RateLimit-Remaining")).hasValue("0");
        JsonNode problem = JSON.readTree(limited.body());
        assertThat(problem.path("status").asInt()).isEqualTo(429);
        assertThat(problem.path("detail").asString()).contains("ANONYMOUS");

        // other clients are unaffected
        assertThat(send(get("/api/v1/trademarks").header("X-Forwarded-For", "10.0.0.20")).statusCode())
                .isEqualTo(200);
    }

    @Test
    void untrustedForwardedFor_cannotBeUsedToDodgeLimits() throws Exception {
        // the peer (127.0.0.1) is trusted here, but the right-most untrusted hop is what counts
        for (int i = 0; i < 5; i++) {
            send(get("/api/v1/stats").header("X-Forwarded-For", "203.0.113." + i + ", 10.9.9.9"));
        }
        HttpResponse<String> res = send(get("/api/v1/stats").header("X-Forwarded-For", "198.51.100.1, 10.9.9.9"));
        assertThat(res.statusCode()).isEqualTo(429);
    }

    @Test
    void loginAndRegister_areLimitedPerIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            HttpResponse<String> ok = send(post("/api/v1/auth/login", "{\"email\":\"a@b.c\",\"password\":\"x\"}")
                    .header("X-Forwarded-For", "10.0.1.1"));
            assertThat(ok.statusCode()).isEqualTo(200);
            assertThat(ok.headers().firstValue("X-RateLimit-Limit")).hasValue("10");
        }
        HttpResponse<String> limited = send(post("/api/v1/auth/login", "{}").header("X-Forwarded-For", "10.0.1.1"));
        assertThat(limited.statusCode()).isEqualTo(429);
        // body was forwarded intact on the allowed calls
        assertThat(STUB.received()).anySatisfy(r -> {
            assertThat(r.path()).isEqualTo("/api/v1/auth/login");
            assertThat(r.bodyText()).contains("a@b.c");
        });
    }

    // ---------- identity ----------

    @Test
    void apiKey_header_isVerifiedOnceAndCached_andNotForwarded() throws Exception {
        int before = STUB.verifyCalls();
        HttpResponse<String> first = send(get("/api/v1/patents/US1").header("X-API-Key", StubDownstream.VALID_KEY));
        HttpResponse<String> second = send(get("/api/v1/patents/US2").header("X-API-Key", StubDownstream.VALID_KEY));

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("X-RateLimit-Limit")).hasValue("3"); // perMinute from verify
        assertThat(STUB.verifyCalls() - before).isEqualTo(1);
        assertThat(JSON.readTree(first.body()).path("headers").has("x-api-key")).isFalse();
    }

    @Test
    void apiKey_queryParam_isAcceptedAndStripped_bodyPreserved() throws Exception {
        HttpResponse<String> res = send(HttpRequest.newBuilder(
                        uri("/api/v1/stats/x?api_key=" + StubDownstream.VALID_KEY + "&page=2"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"hello\":\"world\"}")));
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode echo = JSON.readTree(res.body());
        assertThat(echo.path("query").asString()).isEqualTo("page=2");
        assertThat(echo.path("body").asString()).isEqualTo("{\"hello\":\"world\"}");
    }

    @Test
    void apiKey_invalid_is401ProblemDetail() throws Exception {
        HttpResponse<String> res = send(get("/api/v1/patents").header("X-API-Key", "opto_" + "Z".repeat(40)));
        assertThat(res.statusCode()).isEqualTo(401);
        JsonNode problem = JSON.readTree(res.body());
        assertThat(problem.path("title").asString()).isEqualTo("Unauthorized");
        assertThat(problem.path("detail").asString()).contains("API key");
        assertThat(problem.path("instance").asString()).isEqualTo("/api/v1/patents");
    }

    @Test
    void jwt_userAndAdminTiers() throws Exception {
        HttpResponse<String> user = send(get("/api/v1/patents")
                .header("Authorization", "Bearer " + STUB.validToken("user-a", "USER")));
        assertThat(user.statusCode()).isEqualTo(200);
        assertThat(user.headers().firstValue("X-RateLimit-Limit")).hasValue("300");

        HttpResponse<String> admin = send(get("/api/v1/patents")
                .header("Authorization", "Bearer " + STUB.validToken("admin-a", "USER", "ADMIN")));
        assertThat(admin.headers().firstValue("X-RateLimit-Limit")).hasValue("1200");
        // Authorization is passed through for downstream auth
        assertThat(JSON.readTree(admin.body()).path("headers").path("authorization").asString()).startsWith("Bearer ");
    }

    @Test
    void jwt_expired_onDataRoute_is401() throws Exception {
        String expired = STUB.token("user-b", List.of("USER"), Instant.now().minusSeconds(3_600));
        HttpResponse<String> res = send(get("/api/v1/patents").header("Authorization", "Bearer " + expired));
        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                v -> assertThat(v).contains("invalid_token"));
    }

    @Test
    void staleJwt_onLogin_isIgnoredByGateway() throws Exception {
        HttpResponse<String> res = send(post("/api/v1/auth/register", "{}")
                .header("Authorization", "Bearer garbage")
                .header("X-Forwarded-For", "10.0.1.2"));
        assertThat(res.statusCode()).isEqualTo(200);
    }

    // ---------- ingest (JWT at the gateway) ----------

    @Test
    void ingest_withoutJwt_is401_samplesArePublic() throws Exception {
        HttpResponse<String> jobs = send(get("/api/v1/ingest/jobs"));
        assertThat(jobs.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(jobs.body()).path("status").asInt()).isEqualTo(401);

        assertThat(send(get("/api/v1/ingest/samples")).statusCode()).isEqualTo(200);
        assertThat(send(get("/api/v1/ingest/samples/grant.xml")).statusCode()).isEqualTo(200);
    }

    @Test
    void ingest_withJwt_streamsMultipartUpload() throws Exception {
        String boundary = "----openpto" + UUID.randomUUID();
        byte[] payload = ("<?xml version=\"1.0\"?><doc>" + "x".repeat(300_000) + "</doc>")
                .getBytes(StandardCharsets.UTF_8);
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"a.xml\"\r\n"
                + "Content-Type: application/xml\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] body = concat(head.getBytes(StandardCharsets.UTF_8), payload, tail.getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> res = send(HttpRequest.newBuilder(uri("/api/v1/ingest/uploads"))
                .header("Authorization", "Bearer " + STUB.validToken("uploader", "USER"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));

        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode echo = JSON.readTree(res.body());
        assertThat(echo.path("bodyLength").asInt()).isEqualTo(body.length);
        assertThat(echo.path("headers").path("content-type").asString()).contains(boundary);
    }

    // ---------- streaming, failures ----------

    @Test
    void csvExport_isStreamedThrough() throws Exception {
        HttpResponse<String> res = send(get("/api/v1/datasets/patents.csv").header("X-Forwarded-For", "10.0.2.1"));
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("text/csv"));
        assertThat(res.headers().firstValue("Content-Disposition")).hasValueSatisfying(v -> assertThat(v).contains("patents.csv"));
        assertThat(res.body().lines().count()).isEqualTo(2_001);
    }

    @Test
    void downstreamDown_is502ProblemNamingService_withRateLimitHeaders() throws Exception {
        HttpResponse<String> res = send(post("/api/v1/fees/patent/filing", "{\"applicationType\":\"UTILITY\"}")
                .header("X-Forwarded-For", "10.0.3.1"));
        assertThat(res.statusCode()).isEqualTo(502);
        JsonNode problem = JSON.readTree(res.body());
        assertThat(problem.path("detail").asString()).contains("fee-service");
        assertThat(problem.path("requestId").asString()).isNotBlank();
        assertThat(res.headers().firstValue("X-RateLimit-Limit")).hasValue("5");
    }

    @Test
    void downstreamTooSlow_is504() throws Exception {
        HttpResponse<String> res = send(get("/api/v1/stats/slow").header("X-Forwarded-For", "10.0.3.2"));
        assertThat(res.statusCode()).isEqualTo(504);
        assertThat(JSON.readTree(res.body()).path("detail").asString()).contains("odp-service");
    }

    // ---------- status, docs, CORS, usage ----------

    @Test
    void status_reportsEachDownstream() throws Exception {
        HttpResponse<String> res = send(get("/gateway/status"));
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode services = JSON.readTree(res.body()).path("services");
        assertThat(services).hasSize(3);
        assertThat(services.get(0).path("name").asString()).isEqualTo("odp-service");
        assertThat(services.get(0).path("status").asString()).isEqualTo("UP");
        assertThat(services.get(1).path("name").asString()).isEqualTo("fee-service");
        assertThat(services.get(1).path("status").asString()).isEqualTo("DOWN");
        assertThat(services.get(2).path("status").asString()).isEqualTo("UP");
        assertThat(services.get(0).path("latencyMs").isNumber()).isTrue();
    }

    @Test
    void apiDocs_areRewrittenToGatewayWithApiKeyScheme() throws Exception {
        HttpResponse<String> res = send(get("/v3/api-docs/odp"));
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode spec = JSON.readTree(res.body());
        assertThat(spec.path("servers").get(0).path("url").asString()).isEqualTo("http://localhost:" + port);
        assertThat(spec.path("components").path("securitySchemes").path("ApiKeyAuth").path("name").asString())
                .isEqualTo("X-API-Key");

        assertThat(send(get("/v3/api-docs/fees")).statusCode()).isEqualTo(502);
        assertThat(send(get("/v3/api-docs/unknown")).statusCode()).isEqualTo(404);
    }

    @Test
    void swaggerUi_isServed() throws Exception {
        HttpResponse<String> page = send(get("/swagger-ui.html"));
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("swagger-ui");
        assertThat(send(get("/webjars/swagger-ui/swagger-ui-bundle.js")).statusCode()).isEqualTo(200);
        assertThat(send(get("/swagger-init.js")).body()).contains("/v3/api-docs/ingest");
    }

    @Test
    void corsPreflight_fromWebApp_isAllowed() throws Exception {
        HttpResponse<String> res = send(HttpRequest.newBuilder(uri("/api/v1/patents"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "x-api-key"));
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:4200");

        HttpResponse<String> evil = send(HttpRequest.newBuilder(uri("/api/v1/patents"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "GET"));
        assertThat(evil.statusCode()).isEqualTo(403);
    }

    @Test
    void usage_isFlushedToOdpWithInternalToken() throws Exception {
        send(get("/api/v1/trademarks/97123456").header("X-API-Key", StubDownstream.VALID_KEY));
        int before = STUB.usagePosts().size();
        usageMeter.flush();
        assertThat(STUB.usagePosts()).hasSizeGreaterThan(before);
        StubDownstream.Recorded post = STUB.usagePosts().getLast();
        assertThat(post.headers().get("x-internal-token")).isEqualTo("dev-internal-token-change-me");
        JsonNode entry = JSON.readTree(post.body()).path("entries").get(0);
        assertThat(entry.path("keyId").asString()).isEqualTo(StubDownstream.VALID_KEY_ID);
        assertThat(entry.path("date").asString()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(entry.path("count").asLong()).isPositive();
    }

    @Test
    void actuatorHealth_isPublic_metricsNeedAdmin() throws Exception {
        assertThat(send(get("/actuator/health")).statusCode()).isEqualTo(200);
        assertThat(send(get("/actuator/metrics")).statusCode()).isEqualTo(401);
        assertThat(send(get("/actuator/metrics")
                .header("Authorization", "Bearer " + STUB.validToken("root", "USER", "ADMIN"))).statusCode())
                .isEqualTo(200);
    }

    // ---------- helpers ----------

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private HttpRequest.Builder post(String path, String json) {
        return HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
        return http.send(builder.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] p : parts) {
            length += p.length;
        }
        byte[] out = new byte[length];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
