package gov.openpto.ingest.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import gov.openpto.ingest.TestFixtures;
import gov.openpto.ingest.transform.DocumentFormat;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OdpClientTest {

    private static final String OK = "{\"inserted\":1,\"updated\":1,\"failed\":[{\"id\":\"US2B2\",\"error\":\"bad cpc\"}]}";

    private MockRestServiceServer server;
    private OdpClient client;
    private final List<Duration> sleeps = new ArrayList<>();
    private final List<JsonNode> records = List.of(
            JsonMapper.shared().readTree("{\"patentNumber\":\"US1B2\"}"),
            JsonMapper.shared().readTree("{\"patentNumber\":\"US2B2\"}"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://odp");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OdpClient(builder.build(), TestFixtures.properties(Path.of("x")).odp(), sleeps::add);
    }

    @Test
    void bulkUpsert_patents_sendsTokenAndParsesResponse() {
        server.expect(requestTo("http://odp/internal/v1/patents/bulk-upsert"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-token"))
                .andExpect(content().json("[{\"patentNumber\":\"US1B2\"},{\"patentNumber\":\"US2B2\"}]"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        BulkUpsertResponse response = client.bulkUpsert(DocumentFormat.Target.PATENTS, records);

        assertThat(response.loaded()).isEqualTo(2);
        assertThat(response.failed()).containsExactly(new BulkUpsertResponse.Failure("US2B2", "bad cpc"));
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void bulkUpsert_trademarks_usesTrademarkEndpoint() {
        server.expect(requestTo("http://odp/internal/v1/trademarks/bulk-upsert"))
                .andRespond(withSuccess("{\"inserted\":2,\"updated\":0}", MediaType.APPLICATION_JSON));

        BulkUpsertResponse response = client.bulkUpsert(DocumentFormat.Target.TRADEMARKS, records);

        assertThat(response.inserted()).isEqualTo(2);
        assertThat(response.failed()).isEmpty();
    }

    @Test
    void bulkUpsert_retriesServerErrorsWithExponentialBackoff() {
        server.expect(times(2), requestTo("http://odp/internal/v1/patents/bulk-upsert")).andRespond(withServerError());
        server.expect(requestTo("http://odp/internal/v1/patents/bulk-upsert"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        BulkUpsertResponse response = client.bulkUpsert(DocumentFormat.Target.PATENTS, records);

        assertThat(response.inserted()).isEqualTo(1);
        assertThat(sleeps).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
        server.verify();
    }

    @Test
    void bulkUpsert_retriesConnectionErrorsAndTooManyRequests() {
        server.expect(requestTo("http://odp/internal/v1/patents/bulk-upsert"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("read timed out");
                });
        server.expect(requestTo("http://odp/internal/v1/patents/bulk-upsert"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("http://odp/internal/v1/patents/bulk-upsert"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(client.bulkUpsert(DocumentFormat.Target.PATENTS, records).updated()).isEqualTo(1);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void bulkUpsert_givesUpAfterMaxAttempts() {
        server.expect(times(3), requestTo("http://odp/internal/v1/patents/bulk-upsert"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.bulkUpsert(DocumentFormat.Target.PATENTS, records))
                .isInstanceOfSatisfying(OdpClientException.class, e -> {
                    assertThat(e.retriesExhausted()).isTrue();
                    assertThat(e.getMessage()).contains("after 3 attempts");
                });
        assertThat(sleeps).hasSize(2);
        server.verify();
    }

    @Test
    void bulkUpsert_clientErrorIsNotRetried() {
        server.expect(requestTo("http://odp/internal/v1/patents/bulk-upsert"))
                .andRespond(withBadRequest().body("{\"detail\":\"title required\"}"));

        assertThatThrownBy(() -> client.bulkUpsert(DocumentFormat.Target.PATENTS, records))
                .isInstanceOfSatisfying(OdpClientException.class, e -> {
                    assertThat(e.retriesExhausted()).isFalse();
                    assertThat(e.getMessage()).contains("400").contains("title required");
                });
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void bulkUpsert_interruptedDuringBackoff() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://odp");
        MockRestServiceServer s = MockRestServiceServer.bindTo(builder).build();
        s.expect(requestTo("http://odp/internal/v1/patents/bulk-upsert")).andRespond(withServerError());
        OdpClient c = new OdpClient(builder.build(), TestFixtures.properties(Path.of("x")).odp(),
                d -> {
                    throw new InterruptedException();
                });

        assertThatThrownBy(() -> c.bulkUpsert(DocumentFormat.Target.PATENTS, records))
                .isInstanceOf(OdpClientException.class).hasMessageContaining("Interrupted");
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void emptyBody_isAnError() throws IOException {
        server.expect(requestTo("http://odp/internal/v1/patents/bulk-upsert")).andRespond(withSuccess());

        assertThatThrownBy(() -> client.bulkUpsert(DocumentFormat.Target.PATENTS, records))
                .isInstanceOf(OdpClientException.class).hasMessageContaining("empty body");
    }
}