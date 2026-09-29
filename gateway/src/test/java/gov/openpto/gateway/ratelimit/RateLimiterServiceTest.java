package gov.openpto.gateway.ratelimit;

import gov.openpto.gateway.config.GatewayProperties;
import gov.openpto.gateway.identity.Tier;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    static final class FakeTime implements TimeMeter {
        final AtomicLong nanos = new AtomicLong(1_000_000_000L);

        @Override
        public long currentTimeNanos() {
            return nanos.get();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        void advance(Duration d) {
            nanos.addAndGet(d.toNanos());
        }
    }

    final FakeTime time = new FakeTime();
    final GatewayProperties.RateLimit config = new GatewayProperties.RateLimit(true, null, 0, null, 0);
    final RateLimiterService limiter = new RateLimiterService(config, time);

    @Test
    void contractTierDefaults_areApplied() {
        assertThat(config.limitFor(Tier.ANONYMOUS)).isEqualTo(new GatewayProperties.TierLimit(30, 1_000));
        assertThat(config.limitFor(Tier.FREE)).isEqualTo(new GatewayProperties.TierLimit(120, 20_000));
        assertThat(config.limitFor(Tier.WEB)).isEqualTo(new GatewayProperties.TierLimit(300, 50_000));
        assertThat(config.limitFor(Tier.ADMIN)).isEqualTo(new GatewayProperties.TierLimit(1_200, 1_000_000));
        assertThat(config.authPerMinute()).isEqualTo(10);
    }

    @Test
    void tryConsume_withinMinuteLimit_countsDown() {
        RateLimitDecision first = limiter.tryConsume("ip:1.1.1.1", 30, 1_000);
        assertThat(first.allowed()).isTrue();
        assertThat(first.limit()).isEqualTo(30);
        assertThat(first.remaining()).isEqualTo(29);
        assertThat(first.resetSeconds()).isEqualTo(2); // one token back after 2s of greedy refill

        for (int i = 0; i < 28; i++) {
            limiter.tryConsume("ip:1.1.1.1", 30, 1_000);
        }
        RateLimitDecision last = limiter.tryConsume("ip:1.1.1.1", 30, 1_000);
        assertThat(last.allowed()).isTrue();
        assertThat(last.remaining()).isZero();
        assertThat(last.resetSeconds()).isEqualTo(60);
    }

    @Test
    void tryConsume_overMinuteLimit_rejectsWithRetryAfter_thenRefills() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("key:k", 3, 100).allowed()).isTrue();
        }
        RateLimitDecision rejected = limiter.tryConsume("key:k", 3, 100);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(20); // 60s / 3 tokens
        assertThat(rejected.resetSeconds()).isGreaterThanOrEqualTo(rejected.retryAfterSeconds());

        time.advance(Duration.ofSeconds(20));
        assertThat(limiter.tryConsume("key:k", 3, 100).allowed()).isTrue();
    }

    @Test
    void tryConsume_dailyQuota_isEnforcedAcrossMinutes() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume("user:u", 100, 5).allowed()).isTrue();
        }
        time.advance(Duration.ofMinutes(5));
        RateLimitDecision rejected = limiter.tryConsume("user:u", 100, 5);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isGreaterThan(TimeUnit.HOURS.toSeconds(23));

        time.advance(Duration.ofDays(1));
        assertThat(limiter.tryConsume("user:u", 100, 5).allowed()).isTrue();
    }

    @Test
    void identities_haveIndependentBuckets() {
        limiter.tryConsume("ip:a", 1, 0);
        assertThat(limiter.tryConsume("ip:a", 1, 0).allowed()).isFalse();
        assertThat(limiter.tryConsume("ip:b", 1, 0).allowed()).isTrue();
        assertThat(limiter.bucketCount()).isEqualTo(2);
    }

    @Test
    void bucketMap_isBounded() {
        RateLimiterService small = new RateLimiterService(
                new GatewayProperties.RateLimit(true, null, 0, Duration.ofMinutes(1), 50), time);
        for (int i = 0; i < 500; i++) {
            small.tryConsume("ip:10.0.0." + i, 10, 0);
        }
        assertThat(small.bucketCount()).isLessThanOrEqualTo(50);
    }

    @Test
    void decision_writesHeaders() {
        MockHttpServletResponse ok = new MockHttpServletResponse();
        new RateLimitDecision(true, 120, 119, 1, 0).applyHeaders(ok);
        assertThat(ok.getHeader("X-RateLimit-Limit")).isEqualTo("120");
        assertThat(ok.getHeader("X-RateLimit-Remaining")).isEqualTo("119");
        assertThat(ok.getHeader("X-RateLimit-Reset")).isEqualTo("1");
        assertThat(ok.getHeader("Retry-After")).isNull();

        MockHttpServletResponse limited = new MockHttpServletResponse();
        new RateLimitDecision(false, 30, 0, 7, 2).applyHeaders(limited);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("2");
    }
}
