package gov.openpto.gateway.docs;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocsControllerTest {

    final JsonMapper json = JsonMapper.builder().build();

    @Test
    void rewrite_replacesServers_addsApiKeyScheme_keepsExistingSecurity() {
        ObjectNode spec = (ObjectNode) json.readTree("""
                {"openapi":"3.1.0","servers":[{"url":"http://localhost:8081"}],
                 "components":{"securitySchemes":{"bearerAuth":{"type":"http","scheme":"bearer","bearerFormat":"JWT"}}},
                 "security":[{"bearerAuth":[]}]}
                """);

        ApiDocsController.rewrite(spec, "https://api.openpto.example");

        assertThat(spec.path("servers")).hasSize(1);
        assertThat(spec.path("servers").get(0).path("url").asString()).isEqualTo("https://api.openpto.example");
        assertThat(spec.path("components").path("securitySchemes").path("ApiKeyAuth").path("in").asString())
                .isEqualTo("header");
        assertThat(spec.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asString())
                .isEqualTo("bearer");
        assertThat(spec.path("security")).hasSize(1);
    }

    @Test
    void rewrite_bareSpec_getsComponentsAndOptionalKeySecurity() {
        ObjectNode spec = (ObjectNode) json.readTree("{\"openapi\":\"3.1.0\",\"paths\":{}}");

        ApiDocsController.rewrite(spec, "http://localhost:8080");

        assertThat(spec.path("components").path("securitySchemes").has("ApiKeyAuth")).isTrue();
        assertThat(spec.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(spec.path("security")).hasSize(2); // key OR anonymous
        assertThat(spec.path("security").get(0).has("ApiKeyAuth")).isTrue();
    }
}
