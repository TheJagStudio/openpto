package gov.openpto.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record SubtotalResponse(
        @Schema(example = "Excess claims") String group,
        @Schema(example = "880.00") BigDecimal amount) {
}
