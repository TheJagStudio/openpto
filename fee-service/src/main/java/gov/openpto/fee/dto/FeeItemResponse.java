package gov.openpto.fee.dto;

import gov.openpto.fee.domain.FeeCategory;
import gov.openpto.fee.domain.FeeUnit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record FeeItemResponse(
        @Schema(example = "UTIL_FILING") String feeCode,
        @Schema(example = "Basic filing fee - utility") String description,
        @Schema(example = "PATENT") FeeCategory category,
        @Schema(example = "Filing") String group,
        @Schema(example = "350.00") BigDecimal largeEntity,
        @Schema(example = "140.00") BigDecimal smallEntity,
        @Schema(example = "70.00") BigDecimal microEntity,
        @Schema(example = "EACH") FeeUnit unit) {
}
