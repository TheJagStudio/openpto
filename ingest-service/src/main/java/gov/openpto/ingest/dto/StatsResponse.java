package gov.openpto.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Ingest statistics (own jobs; all jobs for ADMIN)")
public record StatsResponse(
        @Schema(example = "12") long jobs,
        List<ValueCount> byStatus,
        @Schema(example = "4210") long recordsLoaded,
        Instant lastJobAt) {

    public record ValueCount(@Schema(example = "COMPLETED") String value, @Schema(example = "10") long count) {
    }
}