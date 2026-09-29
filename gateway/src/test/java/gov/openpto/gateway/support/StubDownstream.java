package gov.openpto.gateway.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-test fake of odp/fee/ingest: JDK {@link HttpServer} on a random port. Echoes proxied requests as JSON,
 * implements the internal verify/usage endpoints, JWKS, health, api-docs, a slow endpoint, a streamed CSV
 * and an upload sink.
 */
public final class StubDownstream implements AutoCloseable {

    public static final String VALID_KEY = "opto_" + "A".repeat(40);
    public static final String VALID_KEY_ID = "11111111-2222-3333-4444-555555555555";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpServer server;
    private final RSAKey rsaKey;
    private final AtomicInteger verifyCalls = new AtomicInteger();
    private final List<Recorded> usagePosts = new CopyOnWriteArrayList<>();
    private final List<Recorded> received = new CopyOnWriteArrayList<>();

    public record Recorded(String method, String path, String query, Map<String, String> headers, byte[] body) {
        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private StubDownstream() throws Exception {
        this.rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
    }

    public static StubDownstream start() {
        try {
            return new StubDownstream();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A localhost port with nothing listening (for "downstream down" tests). */
    public static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int verifyCalls() {
        return verifyCalls.get();
    }

    public List<Recorded> usagePosts() {
        return usagePosts;
    }

    public List<Recorded> received() {
        return received;
    }

    public String token(String subject, List<String> roles, Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("openpto")
                    .subject(subject)
                    .claim("email", subject + "@example.test")
                    .claim("roles", roles)
                    .issueTime(Date.from(Instant.now().minusSeconds(60)))
                    .expirationTime(Date.from(expiresAt))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey.getKeyID()).type(JOSEObjectType.JWT).build(), claims);
            jwt.sign(new RSASSASigner(rsaKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String validToken(String subject, String... roles) {
        return token(subject, List.of(roles), Instant.now().plusSeconds(3600));
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            byte[] body = readAll(exchange.getRequestBody());
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(Locale.ROOT), String.join(",", v)));
            Recorded recorded = new Recorded(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
                    headers, body);
            received.add(recorded);
            switch (path) {
                case "/actuator/health" -> json(exchange, 200, Map.of("status", "UP"));
                case "/.well-known/jwks.json" ->
                        send(exchange, 200, "application/json", new JWKSet(rsaKey.toPublicJWK()).toString());
                case "/internal/v1/api-keys/verify" -> verify(exchange, headers, body);
                case "/internal/v1/usage" -> {
                    usagePosts.add(recorded);
                    exchange.sendResponseHeaders(204, -1);
                }
                case "/v3/api-docs" -> send(exchange, 200, "application/json",
                        "{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"stub\",\"version\":\"1\"},"
                                + "\"servers\":[{\"url\":\"http://internal:8081\"}],\"paths\":{}}");
                case "/api/v1/stats/slow" -> {
                    sleep(3_000);
                    json(exchange, 200, Map.of("slow", true));
                }
                case "/api/v1/datasets/patents.csv" -> csv(exchange);
                default -> echo(exchange, recorded);
            }
        }
    }

    private void verify(HttpExchange exchange, Map<String, String> headers, byte[] body) throws IOException {
        verifyCalls.incrementAndGet();
        if (!"dev-internal-token-change-me".equals(headers.get("x-internal-token"))) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        JsonNode node = JSON.readTree(body);
        String key = node.path("key").asString();
        Map<String, Object> result = new LinkedHashMap<>();
        if (VALID_KEY.equals(key)) {
            result.put("valid", true);
            result.put("keyId", VALID_KEY_ID);
            result.put("userId", "user-1");
            result.put("tier", "FREE");
            result.put("perMinute", 3);
            result.put("perDay", 100);
        } else {
            result.put("valid", false);
        }
        json(exchange, 200, result);
    }

    private static void csv(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/csv");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"patents.csv\"");
        exchange.sendResponseHeaders(200, 0); // chunked
        try (OutputStream out = exchange.getResponseBody()) {
            out.write("patentNumber,title\n".getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 2_000; i++) {
                out.write(("US" + (10_000_000 + i) + "B2,Title " + i + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static void echo(HttpExchange exchange, Recorded recorded) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", recorded.method());
        result.put("path", recorded.path());
        result.put("query", recorded.query());
        result.put("headers", recorded.headers());
        result.put("bodyLength", recorded.body().length);
        result.put("body", recorded.bodyText().length() <= 2_000 ? recorded.bodyText() : "");
        // services echo the correlation id; the gateway must not duplicate it
        exchange.getResponseHeaders().set("X-Request-Id", String.valueOf(recorded.headers().get("x-request-id")));
        json(exchange, 200, result);
    }

    private static void json(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, "application/json", JSON.writeValueAsString(body));
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (in) {
            return in.readAllBytes();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
