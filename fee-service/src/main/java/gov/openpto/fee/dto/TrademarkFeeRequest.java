package gov.openpto.fee.dto;

import gov.openpto.fee.domain.TrademarkFilingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Trademark fee request; all fees are per class.")
public record TrademarkFeeRequest(
        @NotNull @Schema(example = "APPLICATION") TrademarkFilingType filingType,
        @NotNull @Min(1) @Max(45) @Schema(example = "3", minimum = "1", maximum = "45") Integer numberOfClasses,
        @Schema(example = "false") Boolean insufficientInformation,
        @Schema(example = "false") Boolean freeFormTextIds,
        @Min(0) @Max(100) @Schema(example = "0", minimum = "0", maximum = "100",
                description = "Blocks of 1,000 characters beyond the first") Integer extraCharacterBlocks,
        @Schema(example = "false", description = "Section 8 / Section 9 grace period") Boolean inGracePeriod,
        @Schema(example = "2025-03-01", description = "Selects the fee schedule; defaults to today") LocalDate filingDate) {

    public TrademarkFeeRequest withFilingDate(LocalDate date) {
        return new TrademarkFeeRequest(filingType, numberOfClasses, insufficientInformation, freeFormTextIds,
                extraCharacterBlocks, inGracePeriod, date);
    }
}
