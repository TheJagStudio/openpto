package gov.openpto.ingest.client;

import gov.openpto.ingest.config.AppProperties;
import gov.openpto.ingest.transform.DocumentFormat;

import java.time.Duration;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Client for odp-service internal bulk-upsert endpoints ({@code X-Internal-Token}). Retries 5xx, 429 and
 * connection failures with exponential back-off ({@code maxAttempts}, {@code initialBackoff} × 2^n);
 * other 4xx responses fail immediately.
 */
@Slf4j
@Component
public class OdpClient {

    static final String PATENTS_PATH = "/internal/v1/patents/bulk-upsert";
    static final String TRADEMARKS_PATH = "/internal/v1/trademarks/bulk-upsert";
    static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final AppProperties.Odp properties;
    private final Sleeper sleeper;

    @Autowired
    public OdpClient(@Qualifier("odpRestClient") RestClient odpRestClient, AppProperties properties) {
        this(odpRestClient, properties.odp(), Sleeper.THREAD);
    }

    public OdpClient(RestClient restClient, AppProperties.Odp properties, Sleeper sleeper) {
        this.restClient = restClient;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    public BulkUpsertResponse bulkUpsert(DocumentFormat.Target target, List<JsonNode> records) {
        String path = target == DocumentFormat.Target.TRADEMARKS ? TRADEMARKS_PATH : PATENTS_PATH;
        int maxAttempts = Math.max(1, properties.maxAttempts());
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                BulkUpsertResponse response = send(path, records);
                if (response == null) {
                    throw new OdpClientException("odp-service returned an empty body for " + path, null, false);
                }
                return response;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() != 429) {
                    throw new OdpClientException("odp-service rejected the batch: " + describe(e.getStatusCode(),
                            e.getResponseBodyAsString()), e, false);
                }
                last = e;
            } catch (HttpServerErrorException | ResourceAccessException e) {
                last = e;
            }
            if (attempt < maxAttempts) {
                Duration backoff = properties.initialBackoff().multipliedBy(1L << (attempt - 1));
                log.warn("odp bulk-upsert attempt {}/{} failed ({}), retrying in {} ms", attempt, maxAttempts,
                        last.getMessage(), backoff.toMillis());
                try {
                    sleeper.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new OdpClientException("Interrupted while retrying odp-service", ie, false);
                }
            }
        }
        throw new OdpClientException("odp-service unavailable after " + maxAttempts + " attempts: "
                + last.getMessage(), last, true);
    }

    private BulkUpsertResponse send(String path, List<JsonNode> records) {
        RestClient.RequestBodySpec request = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(TOKEN_HEADER, properties.internalToken());
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            request = request.header("X-Request-Id", requestId);
        }
        return request.body(records).retrieve().body(BulkUpsertResponse.class);
    }

    private static String describe(HttpStatusCode status, String body) {
        String snippet = body == null ? "" : body.length() > 300 ? body.substring(0, 300) + "…" : body;
        return status.value() + (snippet.isBlank() ? "" : " " + snippet);
    }
}