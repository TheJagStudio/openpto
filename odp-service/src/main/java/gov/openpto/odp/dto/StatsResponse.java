package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Portal statistics (patentsByYear = filing year)")
public record StatsResponse(
        @Schema(example = "5000") long patents,
        @Schema(example = "2000") long trademarks,
        List<YearCount> patentsByYear,
        List<FacetCount> trademarksByStatus,
        @Schema(example = "2026-09-28T18:04:11Z", nullable = true) Instant lastIngestAt) {
}
