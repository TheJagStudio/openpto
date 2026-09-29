package gov.openpto.gateway.config;

import gov.openpto.gateway.identity.Tier;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed gateway configuration ({@code gateway.*} in application.yml). Defaults live in application.yml;
 * every downstream URL and the internal token can be overridden with env vars (ODP_URL, FEE_URL,
 * INGEST_URL, INTERNAL_TOKEN).
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        Map<String, Service> services,
        Map<String, Duration> routeReadTimeouts,
        String internalToken,
        List<String> trustedProxies,
        List<String> corsAllowedOrigins,
        String publicBaseUrl,
        String jwtIssuer,
        RateLimit rateLimit,
        ApiKeys apiKeys,
        Usage usage,
        CircuitBreaker circuitBreaker,
        Duration statusTimeout) {

    public static final String ODP = "odp";
    public static final String FEES = "fees";
    public static final String INGEST = "ingest";

    public GatewayProperties {
        services = services == null ? Map.of() : Map.copyOf(services);
        routeReadTimeouts = routeReadTimeouts == null ? Map.of() : Map.copyOf(routeReadTimeouts);
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
        jwtIssuer = jwtIssuer == null ? "openpto" : jwtIssuer;
        statusTimeout = statusTimeout == null ? Duration.ofSeconds(2) : statusTimeout;
        rateLimit = rateLimit == null ? RateLimit.defaults() : rateLimit;
        apiKeys = apiKeys == null ? new ApiKeys(null, null, 0) : apiKeys;
        usage = usage == null ? new Usage(true, 0, 0, 0) : usage;
        circuitBreaker = circuitBreaker == null ? new CircuitBreaker(0, null) : circuitBreaker;
    }

    /** Looks up a configured downstream, failing fast on typos in route definitions. */
    public Service service(String name) {
        return Objects.requireNonNull(services.get(name), () -> "gateway.services." + name + " is not configured");
    }

    /**
     * A downstream service.
     *
     * @param displayName    name used in status output and error messages (e.g. {@code odp-service})
     * @param url            base URL
     * @param connectTimeout TCP connect timeout
     * @param readTimeout    time allowed until the downstream sends response headers
     */
    public record Service(String displayName, URI url, Duration connectTimeout, Duration readTimeout) {
        public Service {
            Objects.requireNonNull(url, "service url");
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        }
    }

    public record TierLimit(long perMinute, long perDay) {
    }

    /**
     * @param tiers            per-tier limits (contract defaults in application.yml)
     * @param authPerMinute    per-IP limit on POST /api/v1/auth/login and /register
     * @param bucketIdleExpiry buckets unused for this long are evicted (bounded memory)
     * @param maxBuckets       hard cap on buckets kept in memory
     */
    public record RateLimit(boolean enabled, Map<Tier, TierLimit> tiers, long authPerMinute,
                            Duration bucketIdleExpiry, long maxBuckets) {
        public RateLimit {
            Map<Tier, TierLimit> merged = new EnumMap<>(Tier.class);
            merged.putAll(Tier.contractDefaults());
            if (tiers != null) {
                merged.putAll(tiers);
            }
            tiers = Map.copyOf(merged);
            authPerMinute = authPerMinute <= 0 ? 10 : authPerMinute;
            bucketIdleExpiry = bucketIdleExpiry == null ? Duration.ofHours(25) : bucketIdleExpiry;
            maxBuckets = maxBuckets <= 0 ? 200_000 : maxBuckets;
        }

        static RateLimit defaults() {
            return new RateLimit(true, null, 0, null, 0);
        }

        public TierLimit limitFor(Tier tier) {
            return tiers.get(tier);
        }
    }

    public record ApiKeys(Duration positiveTtl, Duration negativeTtl, long maxEntries) {
        public ApiKeys {
            positiveTtl = positiveTtl == null ? Duration.ofSeconds(60) : positiveTtl;
            negativeTtl = negativeTtl == null ? Duration.ofSeconds(10) : negativeTtl;
            maxEntries = maxEntries <= 0 ? 50_000 : maxEntries;
        }
    }

    /**
     * @param flushIntervalMs   how often counts are pushed to odp-service
     * @param maxPendingEntries cap on (keyId, date) counters kept when odp-service is unreachable
     * @param batchSize         max entries per POST /internal/v1/usage
     */
    public record Usage(boolean enabled, long flushIntervalMs, int maxPendingEntries, int batchSize) {
        public Usage {
            flushIntervalMs = flushIntervalMs <= 0 ? 30_000 : flushIntervalMs;
            maxPendingEntries = maxPendingEntries <= 0 ? 50_000 : maxPendingEntries;
            batchSize = batchSize <= 0 ? 1_000 : batchSize;
        }
    }

    public record CircuitBreaker(int failureThreshold, Duration openDuration) {
        public CircuitBreaker {
            failureThreshold = failureThreshold <= 0 ? 5 : failureThreshold;
            openDuration = openDuration == null ? Duration.ofSeconds(10) : openDuration;
        }
    }
}
