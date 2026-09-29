package gov.openpto.gateway.usage;

import gov.openpto.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsageMeterTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneOffset.UTC);
    static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Mock
    UsageClient client;

    UsageMeter meter(int maxPending, int batchSize) {
        return new UsageMeter(client, new GatewayProperties.Usage(true, 30_000, maxPending, batchSize), CLOCK);
    }

    @Test
    @SuppressWarnings("unchecked")
    void flush_sendsAggregatedCountsAndClears() {
        UsageMeter meter = meter(100, 100);
        meter.record("k1");
        meter.record("k1");
        meter.record("k2");

        assertThat(meter.flush()).isEqualTo(2);

        ArgumentCaptor<List<UsageEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).send(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                new UsageEntry("k1", TODAY, 2), new UsageEntry("k2", TODAY, 1));
        assertThat(meter.pending("k1", TODAY)).isZero();

        meter.flush();
        verify(client, times(1)).send(anyList()); // nothing new → no call
    }

    @Test
    void flush_failure_keepsCountsAndMergesWithNewOnes() {
        UsageMeter meter = meter(100, 100);
        meter.record("k1");
        meter.record("k1");
        doThrow(new IllegalStateException("odp down")).when(client).send(anyList());

        assertThat(meter.flush()).isZero();
        assertThat(meter.pending("k1", TODAY)).isEqualTo(2);

        meter.record("k1");
        doNothing().when(client).send(anyList());
        assertThat(meter.flush()).isEqualTo(1);
        verify(client).send(List.of(new UsageEntry("k1", TODAY, 3)));
        assertThat(meter.pending("k1", TODAY)).isZero();
    }

    @Test
    void flush_partialBatchFailure_keepsOnlyUndelivered() {
        UsageMeter meter = meter(100, 1);
        meter.record("a");
        meter.record("b");
        meter.record("c");
        doNothing().doThrow(new IllegalStateException("boom")).when(client).send(anyList());

        assertThat(meter.flush()).isEqualTo(1);
        assertThat(meter.pendingEntries()).isEqualTo(3); // counters are reused; 2 of them still hold counts
        long pendingTotal = meter.pending("a", TODAY) + meter.pending("b", TODAY) + meter.pending("c", TODAY);
        assertThat(pendingTotal).isEqualTo(2);
    }

    @Test
    void record_isBounded_whenBufferFull() {
        UsageMeter meter = meter(2, 100);
        meter.record("a");
        meter.record("b");
        meter.record("c"); // dropped
        meter.record("a"); // existing counter still counts
        assertThat(meter.pending("a", TODAY)).isEqualTo(2);
        assertThat(meter.pending("c", TODAY)).isZero();
    }

    @Test
    void flush_dropsDrainedCountersOfPastDays_keepsTodays() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T23:59:00Z"));
        UsageMeter meter = new UsageMeter(client, new GatewayProperties.Usage(true, 30_000, 100, 100), clock);
        meter.record("x");
        clock.now = Instant.parse("2026-09-28T00:01:00Z");
        meter.record("x");

        assertThat(meter.flush()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UsageEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).send(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                new UsageEntry("x", TODAY.minusDays(1), 1), new UsageEntry("x", TODAY, 1));
        assertThat(meter.pendingEntries()).isEqualTo(1); // yesterday's counter removed, today's reused
    }

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void disabledOrNullKey_recordsNothing() {
        UsageMeter disabled = new UsageMeter(client, new GatewayProperties.Usage(false, 0, 0, 0), CLOCK);
        disabled.record("k");
        meter(10, 10).record(null);
        disabled.flushOnShutdown();
        verify(client, never()).send(anyList());
    }
}
