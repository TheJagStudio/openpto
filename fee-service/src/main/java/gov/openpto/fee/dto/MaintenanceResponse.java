package gov.openpto.fee.dto;

import gov.openpto.fee.domain.EntitySize;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Maintenance windows for a patent evaluated on asOfDate.")
public record MaintenanceResponse(
        List<MaintenanceWindowResponse> windows,
        @Schema(example = "false") boolean patentExpired,
        @Schema(example = "FY2025") String scheduleCode,
        @Schema(example = "USPTO fee schedule effective January 19, 2025 (illustrative)") String scheduleName,
        @Schema(example = "SMALL") EntitySize entitySize,
        @Schema(example = "2021-06-15") LocalDate grantDate,
        @Schema(example = "2025-02-01") LocalDate asOfDate,
        @Schema(description = "Itemized fees payable on asOfDate (empty if none)") FeeQuoteResponse payableNow) {
}
