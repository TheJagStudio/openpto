package gov.openpto.odp.dto;

import gov.openpto.odp.model.MarkType;
import gov.openpto.odp.model.TrademarkStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Trademark search hit")
public record TrademarkSummary(
        @Schema(example = "97123456") String serialNumber,
        @Schema(example = "7012345") String registrationNumber,
        @Schema(example = "BLUE HERON ROASTERS") String markText,
        @Schema(example = "STANDARD_CHARACTER") MarkType markType,
        @Schema(example = "LIVE_REGISTERED") TrademarkStatus status,
        @Schema(example = "2021-06-02") LocalDate filingDate,
        @Schema(example = "2022-09-13") LocalDate registrationDate,
        @Schema(example = "Blue Heron Coffee Co.") String owner,
        @Schema(example = "[30, 43]") List<Integer> niceClasses) {
}
