package gov.openpto.fee.dto;

import gov.openpto.fee.domain.EntitySize;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Itemized fee quote. total = sum of lineItems[].amount; money has 2 decimals.")
public record FeeQuoteResponse(
        @Schema(example = "FY2025") String scheduleCode,
        @Schema(example = "USPTO fee schedule effective January 19, 2025 (illustrative)") String scheduleName,
        @Schema(example = "SMALL", nullable = true, description = "Null for trademark quotes") EntitySize entitySize,
        List<LineItemResponse> lineItems,
        List<SubtotalResponse> subtotals,
        @Schema(example = "1880.00") BigDecimal total,
        @Schema(example = "USD") String currency,
        List<String> notes,
        List<String> warnings) {

    public static final String CURRENCY = "USD";
}
