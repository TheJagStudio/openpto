package gov.openpto.odp.controller;

import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.config.SecurityConfig;
import gov.openpto.odp.security.ProblemResponseWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** Security + problem-writer wiring shared by the {@code @WebMvcTest} slices. */
@TestConfiguration
@Import({SecurityConfig.class, ProblemResponseWriter.class})
@EnableConfigurationProperties(AppProperties.Security.class)
class WebSliceConfig {

}
