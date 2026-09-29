package gov.openpto.odp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    public static final String INTERNAL = "internalToken";

    @Bean
    OpenAPI odpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpenPTO Open Data Portal API")
                        .version("v1")
                        .description("""
                                Patent and trademark search (PostgreSQL full-text), bulk datasets, portal \
                                accounts, API keys and usage. Identity provider for the OpenPTO platform \
                                (RS256 JWTs, JWKS at /.well-known/jwks.json).""")
                        .license(new License().name("Apache-2.0")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /api/v1/auth/login"))
                        .addSecuritySchemes(INTERNAL, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Token")
                                .description("Shared secret for service-to-service calls; never routed by the gateway")))
                .tags(List.of(
                        new Tag().name("Patents").description("Patent search, facets and detail"),
                        new Tag().name("Trademarks").description("Trademark search and TSDR-style detail"),
                        new Tag().name("Stats").description("Portal statistics"),
                        new Tag().name("Datasets").description("Streamed bulk exports (JSON/CSV)"),
                        new Tag().name("Auth").description("Registration, login and the current user"),
                        new Tag().name("Account").description("API keys and usage of the signed-in user"),
                        new Tag().name("Admin").description("Administration (ROLE_ADMIN)"),
                        new Tag().name("Internal").description("Service-to-service endpoints (X-Internal-Token)"),
                        new Tag().name("Identity").description("JWKS for token verification")));
    }
}
