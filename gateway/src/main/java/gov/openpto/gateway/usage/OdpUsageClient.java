package gov.openpto.gateway.usage;

import gov.openpto.gateway.config.GatewayProperties;
import gov.openpto.gateway.web.GatewayHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** Posts usage batches to odp-service with the internal token. */
@Component
public class OdpUsageClient implements UsageClient {

    private final RestClient restClient;
    private final URI usageUri;
    private final String internalToken;

    public OdpUsageClient(RestClient serviceRestClient, GatewayProperties properties) {
        this.restClient = serviceRestClient;
        this.usageUri = UriComponentsBuilder.fromUri(properties.service(GatewayProperties.ODP).url())
                .path("/internal/v1/usage").build().toUri();
        this.internalToken = properties.internalToken();
    }

    @Override
    public void send(List<UsageEntry> entries) {
        restClient.post()
                .uri(usageUri)
                .header(GatewayHeaders.INTERNAL_TOKEN, internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("entries", entries))
                .retrieve()
                .toBodilessEntity();
    }
}
