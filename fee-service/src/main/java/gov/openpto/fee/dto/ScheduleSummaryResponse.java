package gov.openpto.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record ScheduleSummaryResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "FY2025") String code,
        @Schema(example = "USPTO fee schedule effective January 19, 2025 (illustrative)") String name,
        @Schema(example = "2025-01-19") LocalDate effectiveFrom,
        @Schema(nullable = true, description = "Null = open-ended") LocalDate effectiveTo,
        @Schema(example = "true", description = "Effective today") boolean current) {
}
