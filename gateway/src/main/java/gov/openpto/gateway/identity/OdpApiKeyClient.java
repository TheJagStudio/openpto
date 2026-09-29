package gov.openpto.gateway.identity;

import gov.openpto.gateway.config.GatewayProperties;
import gov.openpto.gateway.web.GatewayHeaders;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/** Calls odp-service {@code POST /internal/v1/api-keys/verify} with the shared internal token. */
@Component
public class OdpApiKeyClient implements ApiKeyVerificationClient {

    private final RestClient restClient;
    private final URI verifyUri;
    private final String internalToken;
    private final String serviceName;

    public OdpApiKeyClient(RestClient serviceRestClient, GatewayProperties properties) {
        GatewayProperties.Service odp = properties.service(GatewayProperties.ODP);
        this.restClient = serviceRestClient;
        this.verifyUri = UriComponentsBuilder.fromUri(odp.url()).path("/internal/v1/api-keys/verify").build().toUri();
        this.internalToken = properties.internalToken();
        this.serviceName = odp.displayName();
    }

    @Override
    public ApiKeyVerification verify(String apiKey) {
        try {
            return restClient.post()
                    .uri(verifyUri)
                    .header(GatewayHeaders.INTERNAL_TOKEN, internalToken)
                    .headers(h -> {
                        String requestId = MDC.get(GatewayHeaders.MDC_REQUEST_ID);
                        if (requestId != null) {
                            h.set(GatewayHeaders.REQUEST_ID, requestId);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("key", apiKey))
                    .retrieve()
                    .body(ApiKeyVerification.class);
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.NotFound e) {
            // odp rejects malformed/unknown keys with 4xx on some paths: treat as invalid, not as an outage
            return ApiKeyVerification.invalid();
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException(serviceName, "API key verification failed", e);
        }
    }
}
