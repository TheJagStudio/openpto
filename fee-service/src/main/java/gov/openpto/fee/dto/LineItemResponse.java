package gov.openpto.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record LineItemResponse(
        @Schema(example = "CLAIM_OVER_20") String feeCode,
        @Schema(example = "Each claim in excess of 20") String description,
        @Schema(example = "5") int quantity,
        @Schema(example = "80.00") BigDecimal unitAmount,
        @Schema(example = "400.00") BigDecimal amount) {
}
