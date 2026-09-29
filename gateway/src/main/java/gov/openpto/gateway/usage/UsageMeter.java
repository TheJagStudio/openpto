package gov.openpto.gateway.usage;

import gov.openpto.gateway.config.GatewayProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts requests per API key per UTC day and flushes them to odp-service every 30s (API Gateway usage
 * plan metering). Draining subtracts exactly what was read ({@code add(-n)}), so increments racing with a
 * flush are never lost. When odp-service is unreachable the drained counts are merged back and retried
 * on the next flush; the number of pending counters is capped so an outage cannot exhaust memory.
 * Pending counts are flushed once more on shutdown.
 */
@Component
public class UsageMeter {

    private static final Logger log = LoggerFactory.getLogger(UsageMeter.class);

    private final ConcurrentHashMap<UsageKey, LongAdder> counts = new ConcurrentHashMap<>();
    private final AtomicLong dropped = new AtomicLong();
    private final UsageClient client;
    private final GatewayProperties.Usage config;
    private final Clock clock;

    record UsageKey(String keyId, LocalDate date) {
    }

    @Autowired
    public UsageMeter(UsageClient client, GatewayProperties properties) {
        this(client, properties.usage(), Clock.systemUTC());
    }

    UsageMeter(UsageClient client, GatewayProperties.Usage config, Clock clock) {
        this.client = client;
        this.config = config;
        this.clock = clock;
    }

    /** Records one allowed request made with the given API key id. */
    public void record(String keyId) {
        if (!config.enabled() || keyId == null) {
            return;
        }
        UsageKey key = new UsageKey(keyId, LocalDate.now(clock));
        LongAdder adder = counts.get(key);
        if (adder == null) {
            if (counts.size() >= config.maxPendingEntries()) {
                dropped.incrementAndGet();
                return;
            }
            adder = counts.computeIfAbsent(key, k -> new LongAdder());
        }
        adder.increment();
    }

    /**
     * Sends all pending counts in batches.
     *
     * @return number of entries successfully delivered
     */
    @Scheduled(fixedDelayString = "${gateway.usage.flush-interval-ms:30000}",
            initialDelayString = "${gateway.usage.flush-interval-ms:30000}")
    public synchronized int flush() {
        List<UsageEntry> drained = drain();
        long lost = dropped.getAndSet(0);
        if (lost > 0) {
            log.warn("Usage metering dropped {} increments because {} counters were pending", lost,
                    config.maxPendingEntries());
        }
        int delivered = 0;
        for (int from = 0; from < drained.size(); from += config.batchSize()) {
            List<UsageEntry> batch = drained.subList(from, Math.min(drained.size(), from + config.batchSize()));
            try {
                client.send(batch);
                delivered += batch.size();
            } catch (RuntimeException e) {
                List<UsageEntry> undelivered = drained.subList(from, drained.size());
                mergeBack(undelivered);
                log.warn("Usage flush to odp-service failed ({}); keeping {} entries for the next attempt",
                        e.getMessage(), undelivered.size());
                break;
            }
        }
        return delivered;
    }

    @PreDestroy
    public void flushOnShutdown() {
        int delivered = flush();
        if (!counts.isEmpty()) {
            log.warn("Shutting down with {} usage counters not delivered", counts.size());
        } else if (delivered > 0) {
            log.info("Flushed {} usage entries on shutdown", delivered);
        }
    }

    /** Pending (not yet delivered) count for a key today; for tests and diagnostics. */
    long pending(String keyId, LocalDate date) {
        LongAdder adder = counts.get(new UsageKey(keyId, date));
        return adder == null ? 0 : adder.sum();
    }

    int pendingEntries() {
        return counts.size();
    }

    private List<UsageEntry> drain() {
        LocalDate today = LocalDate.now(clock);
        List<UsageEntry> drained = new ArrayList<>();
        for (Map.Entry<UsageKey, LongAdder> e : counts.entrySet()) {
            long n = e.getValue().sum();
            if (n > 0) {
                e.getValue().add(-n);
                drained.add(new UsageEntry(e.getKey().keyId(), e.getKey().date(), n));
            }
        }
        // counters for past days receive no more increments once drained; today's are reused
        counts.entrySet().removeIf(e -> e.getKey().date().isBefore(today) && e.getValue().sum() == 0);
        return drained;
    }

    private void mergeBack(List<UsageEntry> entries) {
        for (UsageEntry entry : entries) {
            counts.computeIfAbsent(new UsageKey(entry.keyId(), entry.date()), k -> new LongAdder()).add(entry.count());
        }
        int excess = counts.size() - config.maxPendingEntries();
        if (excess > 0) {
            List<UsageKey> oldest = counts.keySet().stream()
                    .sorted(Comparator.comparing(UsageKey::date))
                    .limit(excess)
                    .toList();
            long lostRequests = 0;
            for (UsageKey key : oldest) {
                LongAdder removed = counts.remove(key);
                lostRequests += removed == null ? 0 : removed.sum();
            }
            log.warn("Usage buffer full: discarded {} oldest counters ({} requests)", oldest.size(), lostRequests);
        }
    }
}
