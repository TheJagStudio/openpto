package gov.openpto.gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gov.openpto.gateway.config.GatewayProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.VerboseResult;
import io.github.bucket4j.local.LocalBucketBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * In-memory Bucket4j limiter (the API Gateway "throttling + quota" equivalent). One bucket per identity with
 * two bandwidths: a per-minute greedy refill (smooth throttling) and a per-day interval refill (quota).
 * Buckets live in a Caffeine map with {@code expireAfterAccess} and a size cap, so memory stays bounded
 * no matter how many distinct IPs show up.
 */
@Component
public class RateLimiterService {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration DAY = Duration.ofDays(1);
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final Cache<String, Bucket> buckets;
    private final TimeMeter timeMeter;

    @Autowired
    public RateLimiterService(GatewayProperties properties) {
        this(properties.rateLimit(), null);
    }

    RateLimiterService(GatewayProperties.RateLimit config, TimeMeter timeMeter) {
        this.timeMeter = timeMeter;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(config.bucketIdleExpiry())
                .maximumSize(config.maxBuckets())
                .build();
    }

    /**
     * Consumes one token.
     *
     * @param bucketKey identity partition, e.g. {@code ip:1.2.3.4}
     * @param perMinute minute capacity (must be &gt; 0)
     * @param perDay    day capacity, or 0 for no daily quota
     */
    public RateLimitDecision tryConsume(String bucketKey, long perMinute, long perDay) {
        Bucket bucket = buckets.get(bucketKey + '|' + perMinute + '|' + perDay, k -> newBucket(perMinute, perDay));
        VerboseResult<ConsumptionProbe> result = bucket.asVerbose().tryConsumeAndReturnRemaining(1);
        ConsumptionProbe probe = result.getValue();
        long minuteAvailable = result.getDiagnostics().getAvailableTokensPerEachBandwidth()[0];
        long resetSeconds = ceilDiv((perMinute - Math.max(0, minuteAvailable)) * MINUTE.toNanos(), perMinute);
        if (probe.isConsumed()) {
            return new RateLimitDecision(true, perMinute, probe.getRemainingTokens(), resetSeconds, 0);
        }
        long retryAfter = Math.max(1, ceilDiv(probe.getNanosToWaitForRefill(), 1));
        return new RateLimitDecision(false, perMinute, 0, Math.max(resetSeconds, retryAfter), retryAfter);
    }

    long bucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }

    private Bucket newBucket(long perMinute, long perDay) {
        LocalBucketBuilder builder = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, MINUTE).id("minute").build());
        if (perDay > 0) {
            builder.addLimit(Bandwidth.builder().capacity(perDay).refillIntervally(perDay, DAY).id("day").build());
        }
        if (timeMeter != null) {
            builder.withCustomTimePrecision(timeMeter);
        }
        return builder.build();
    }

    /** ceil(nanos / divisor) expressed in seconds. */
    private static long ceilDiv(long nanos, long divisor) {
        long perUnit = Math.ceilDiv(nanos, divisor);
        return Math.ceilDiv(perUnit, NANOS_PER_SECOND);
    }
}
