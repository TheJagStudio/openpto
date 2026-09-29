package gov.openpto.gateway.docs;

import gov.openpto.gateway.config.GatewayProperties;
import gov.openpto.gateway.web.ProblemResponses;
import gov.openpto.gateway.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Aggregated OpenAPI: {@code /v3/api-docs/{odp|fees|ingest}} fetches the service's {@code /v3/api-docs},
 * points {@code servers} at the gateway (so Swagger UI "Try it out" goes through the gateway, limits and all)
 * and adds the {@code X-API-Key} security scheme. The Swagger UI at {@code /swagger-ui.html} lists all three.
 */
@RestController
public class ApiDocsController {

    private static final Logger log = LoggerFactory.getLogger(ApiDocsController.class);
    private static final Map<String, String> SPECS = Map.of(
            "odp", GatewayProperties.ODP, "fees", GatewayProperties.FEES, "ingest", GatewayProperties.INGEST);

    private final RestClient restClient;
    private final GatewayProperties properties;

    public ApiDocsController(RestClient serviceRestClient, GatewayProperties properties) {
        this.restClient = serviceRestClient;
        this.properties = properties;
    }

    @GetMapping(value = "/v3/api-docs/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> apiDocs(@PathVariable String name, HttpServletRequest request) {
        String serviceKey = SPECS.get(name);
        if (serviceKey == null) {
            return problem(request, HttpStatus.NOT_FOUND, "Unknown API spec '" + name + "'. Use odp, fees or ingest.");
        }
        GatewayProperties.Service service = properties.service(serviceKey);
        JsonNode spec;
        try {
            spec = restClient.get()
                    .uri(UriComponentsBuilder.fromUri(service.url()).path("/v3/api-docs").build().toUri())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Fetching api-docs from {} failed: {}", service.displayName(), e.getMessage());
            return problem(request, HttpStatus.BAD_GATEWAY,
                    "API docs for " + service.displayName() + " are unavailable: the service did not respond.");
        }
        if (!(spec instanceof ObjectNode root)) {
            return problem(request, HttpStatus.BAD_GATEWAY, service.displayName() + " returned an invalid OpenAPI document.");
        }
        rewrite(root, baseUrl());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(root);
    }

    static void rewrite(ObjectNode spec, String gatewayBaseUrl) {
        ArrayNode servers = spec.putArray("servers");
        servers.addObject().put("url", gatewayBaseUrl).put("description", "OpenPTO API gateway");

        ObjectNode components = objectChild(spec, "components");
        ObjectNode schemes = objectChild(components, "securitySchemes");
        schemes.putObject("ApiKeyAuth")
                .put("type", "apiKey")
                .put("in", "header")
                .put("name", "X-API-Key")
                .put("description", "Developer API key (opto_...). Optional: without it requests are "
                        + "limited per client IP. Also accepted as ?api_key=.");
        if (!schemes.has("bearerAuth")) {
            schemes.putObject("bearerAuth").put("type", "http").put("scheme", "bearer").put("bearerFormat", "JWT");
        }
        if (!spec.has("security")) {
            ArrayNode security = spec.putArray("security");
            security.addObject().putArray("ApiKeyAuth");
            security.addObject();
        }
    }

    private static ObjectNode objectChild(ObjectNode parent, String field) {
        JsonNode existing = parent.get(field);
        return existing instanceof ObjectNode node ? node : parent.putObject(field);
    }

    private String baseUrl() {
        if (properties.publicBaseUrl() != null && !properties.publicBaseUrl().isBlank()) {
            return properties.publicBaseUrl();
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private static ResponseEntity<Map<String, Object>> problem(HttpServletRequest request, HttpStatus status,
                                                               String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemResponses.body(status, detail, request.getRequestURI(),
                        request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }
}
