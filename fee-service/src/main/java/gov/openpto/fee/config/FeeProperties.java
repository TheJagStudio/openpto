package gov.openpto.fee.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.fees.*} settings. */
@ConfigurationProperties(prefix = "app.fees")
public record FeeProperties(ZoneId zone) {

    public FeeProperties {
        zone = zone == null ? ZoneId.of("America/New_York") : zone;
    }
}
