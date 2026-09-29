package gov.openpto.fee.dto;

import gov.openpto.fee.domain.MaintenanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MaintenanceWindowResponse(
        @Schema(example = "3.5", allowableValues = {"3.5", "7.5", "11.5"}) String stage,
        @Schema(example = "2024-06-15") LocalDate windowOpens,
        @Schema(example = "2024-12-15") LocalDate dueDate,
        @Schema(example = "2025-06-15") LocalDate graceEnds,
        @Schema(example = "GRACE_PERIOD") MaintenanceStatus status,
        @Schema(example = "860.00") BigDecimal fee,
        @Schema(example = "216.00", description = "Grace-period surcharge for this stage") BigDecimal surcharge,
        @Schema(example = "1076.00", nullable = true,
                description = "Fee (+ surcharge in grace) if paid on asOfDate; null when not payable") BigDecimal totalIfPaidOnAsOfDate) {
}
