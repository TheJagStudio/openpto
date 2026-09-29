package gov.openpto.odp.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Kept off the application class so test slices do not inherit caching/property scanning. */
@Configuration(proxyBeanMethods = false)
@EnableCaching
@ConfigurationPropertiesScan(basePackageClasses = AppProperties.class)
public class AppConfig {

    public static final String STATS_CACHE = "stats";
    public static final String FACETS_CACHE = "patentFacets";
}
