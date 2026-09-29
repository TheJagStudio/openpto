package gov.openpto.fee.config;

import gov.openpto.fee.domain.MaintenanceCalculator;
import gov.openpto.fee.domain.PatentFilingCalculator;
import gov.openpto.fee.domain.TrademarkFeeCalculator;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free calculation core into Spring. The calculators are plain classes; registering them
 * as beans lets services receive them by constructor injection (and tests mock them).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(FeeProperties.class)
public class FeeConfig {

    public static final String SCHEDULE_CACHE = "feeSchedules";

    @Bean
    Clock clock(FeeProperties properties) {
        return Clock.system(properties.zone());
    }

    @Bean
    PatentFilingCalculator patentFilingCalculator() {
        return new PatentFilingCalculator();
    }

    @Bean
    MaintenanceCalculator maintenanceCalculator() {
        return new MaintenanceCalculator();
    }

    @Bean
    TrademarkFeeCalculator trademarkFeeCalculator() {
        return new TrademarkFeeCalculator();
    }
}
