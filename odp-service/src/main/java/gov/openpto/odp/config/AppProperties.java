package gov.openpto.odp.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Typed {@code app.*} configuration. */
public final class AppProperties {

    private AppProperties() {
    }

    @ConfigurationProperties("app.jwt")
    public record Jwt(
            @DefaultValue("openpto") String issuer,
            @DefaultValue("8h") Duration ttl,
            @DefaultValue("../data/keys") String keyDir) {

        /** Relative paths resolve against the working directory (the service folder in dev). */
        public Path keyPath() {
            return Path.of(keyDir);
        }
    }

    @ConfigurationProperties("app.security")
    public record Security(
            @DefaultValue("dev-internal-token-change-me") String internalToken,
            @DefaultValue("5") int maxFailedLogins,
            @DefaultValue("15m") Duration lockoutDuration) {

        @Override
        public String toString() {
            return "Security[internalToken=***, maxFailedLogins=" + maxFailedLogins + ", lockoutDuration=" + lockoutDuration + "]";
        }
    }

    @ConfigurationProperties("app.admin")
    public record Admin(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("admin@openpto.local") String email,
            @DefaultValue("Admin#12345") String password,
            @DefaultValue("OpenPTO Admin") String displayName) {

        @Override
        public String toString() {
            return "Admin[enabled=" + enabled + ", email=" + email + ", password=***]";
        }
    }

    @ConfigurationProperties("app.api-keys")
    public record ApiKeys(@DefaultValue("5") int maxActivePerUser) {
    }

    @ConfigurationProperties("app.export")
    public record Export(@DefaultValue("10000") int maxRows) {
    }

    @ConfigurationProperties("app.seed")
    public record Seed(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("5000") int patents,
            @DefaultValue("2000") int trademarks,
            @DefaultValue("20250119") long randomSeed) {
    }
}
