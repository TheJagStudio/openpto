package gov.openpto.odp.service;

import gov.openpto.odp.config.AppConfig;
import gov.openpto.odp.dto.FacetCount;
import gov.openpto.odp.dto.StatsResponse;
import gov.openpto.odp.dto.YearCount;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.repository.PatentRepository;
import gov.openpto.odp.repository.TrademarkRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Portal statistics; cached briefly and evicted whenever ingest writes data. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatsService {

    private final PatentRepository patents;
    private final TrademarkRepository trademarks;

    @Cacheable(cacheNames = AppConfig.STATS_CACHE, key = "'all'")
    public StatsResponse stats() {
        List<YearCount> byYear = patents.countByFilingYear().stream()
                .map(r -> new YearCount(((Number) r[0]).intValue(), ((Number) r[1]).longValue()))
                .toList();
        List<FacetCount> byStatus = trademarks.countByStatus().stream()
                .map(r -> new FacetCount(String.valueOf(r[0]), ((Number) r[1]).longValue()))
                .toList();
        Instant lastIngest = Stream.of(
                        patents.lastUpdatedAt(RecordSource.INGEST), trademarks.lastUpdatedAt(RecordSource.INGEST))
                .flatMap(Optional::stream)
                .max(Instant::compareTo)
                .orElse(null);
        return new StatsResponse(patents.count(), trademarks.count(), byYear, byStatus, lastIngest);
    }
}
