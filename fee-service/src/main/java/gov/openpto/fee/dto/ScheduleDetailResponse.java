package gov.openpto.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record ScheduleDetailResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "FY2025") String code,
        @Schema(example = "USPTO fee schedule effective January 19, 2025 (illustrative)") String name,
        @Schema(example = "2025-01-19") LocalDate effectiveFrom,
        @Schema(nullable = true) LocalDate effectiveTo,
        @Schema(example = "true") boolean current,
        List<FeeItemResponse> items) {
}
