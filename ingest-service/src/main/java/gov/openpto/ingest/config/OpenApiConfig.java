package gov.openpto.ingest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI ingestOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpenPTO Ingest API")
                        .version("v1")
                        .description("Upload legacy USPTO bulk XML (patent grants, application publications, 2001-2004 "
                                + "PATDOC red book, trademark daily files). Files land in the raw bucket, an S3-style "
                                + "ObjectCreated event triggers the transform function (Lambda), the JSON output lands "
                                + "in the processed bucket and is bulk-loaded into the Open Data Portal.")
                        .license(new License().name("Apache-2.0")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("RS256 access token from POST /api/v1/auth/login (odp-service)")))
                .addTagsItem(new Tag().name("Ingest").description("Uploads and ingest jobs (JWT required)"))
                .addTagsItem(new Tag().name("Samples").description("Public sample files"));
    }
}