package gov.openpto.odp.service;

import gov.openpto.odp.dto.UsageReportRequest;
import gov.openpto.odp.dto.UsageResponse;
import gov.openpto.odp.exception.BadRequestException;
import gov.openpto.odp.repository.UsageRepository;
import gov.openpto.odp.repository.UsageRepository.DailyCount;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-key daily usage: ingestion of the gateway's batched counters and the account usage report. */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UsageService {

    public static final int MAX_DAYS = 365;

    private final UsageRepository usage;
    private final Clock clock;

    /**
     * Upsert-adds each (key, date) count and bumps the keys' lifetime counters. Duplicate
     * (key, date) entries in one request are summed first; unknown keys are skipped.
     */
    @Transactional
    public int record(UsageReportRequest request) {
        Map<DailyKey, Long> merged = new HashMap<>();
        for (UsageReportRequest.Entry e : request.entries()) {
            if (e.count() > 0) {
                merged.merge(new DailyKey(e.keyId(), e.date()), e.count(), Long::sum);
            }
        }
        List<DailyCount> counts = merged.entrySet().stream()
                .map(en -> new DailyCount(en.getKey().keyId(), en.getKey().date(), en.getValue()))
                .sorted(Comparator.comparing(DailyCount::keyId).thenComparing(DailyCount::date))
                .toList();
        int applied = usage.addDailyCounts(counts);
        Map<UUID, Long> perKey = new TreeMap<>();
        counts.forEach(c -> perKey.merge(c.keyId(), c.count(), Long::sum));
        usage.bumpKeys(perKey, clock.instant());
        log.debug("Recorded {} usage entries ({} applied) for {} keys", counts.size(), applied, perKey.size());
        return applied;
    }

    /** Usage of all the user's keys over the last {@code days} days (today inclusive), zero-filled. */
    public UsageResponse forUser(UUID userId, int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new BadRequestException("days must be between 1 and " + MAX_DAYS);
        }
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays(days - 1L);
        Map<LocalDate, Long> byDate = new HashMap<>();
        usage.dailyTotalsForUser(userId, from, to).forEach(d -> byDate.put(d.date(), d.requests()));
        List<UsageResponse.DailyUsage> daily = new ArrayList<>(days);
        long total = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long n = byDate.getOrDefault(d, 0L);
            total += n;
            daily.add(new UsageResponse.DailyUsage(d, n));
        }
        List<UsageResponse.KeyUsage> byKey = usage.keyTotalsForUser(userId, from, to).stream()
                .map(k -> new UsageResponse.KeyUsage(k.keyId(), k.name(), k.requests()))
                .toList();
        return new UsageResponse(total, daily, byKey);
    }

    private record DailyKey(UUID keyId, LocalDate date) {
    }
}
