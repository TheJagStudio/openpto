package gov.openpto.fee.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String TAG_SCHEDULES = "Fee schedules";
    public static final String TAG_CALCULATORS = "Fee calculators";
    public static final String TAG_QUOTES = "Saved quotes";

    @Bean
    OpenAPI feeServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpenPTO Fee Service")
                        .version("v1")
                        .description("Decoupled USPTO-style fee calculator: versioned fee schedules, patent filing, "
                                + "maintenance and trademark fee quotes, saved quotes. Amounts are illustrative.")
                        .license(new License().name("Apache-2.0")))
                .tags(List.of(
                        new Tag().name(TAG_SCHEDULES).description("Versioned fee schedules with large/small/micro amounts"),
                        new Tag().name(TAG_CALCULATORS).description("Itemized fee calculations"),
                        new Tag().name(TAG_QUOTES).description("Save and share quotes (recomputed server-side)")));
    }
}
