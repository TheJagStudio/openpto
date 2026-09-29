package gov.openpto.fee.dto;

import gov.openpto.fee.domain.ApplicationType;
import gov.openpto.fee.domain.ContinuedExamination;
import gov.openpto.fee.domain.EntitySize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Patent filing-fee request. Omitted numbers default to 0, filedElectronically to true, "
        + "continuedExamination to NONE, filingDate to today (US Eastern).")
public record PatentFilingRequest(
        @NotNull @Schema(example = "UTILITY") ApplicationType applicationType,
        @NotNull @Schema(example = "SMALL") EntitySize entitySize,
        @Min(0) @Max(500) @Schema(example = "25", minimum = "0", maximum = "500") Integer totalClaims,
        @Min(0) @Max(100) @Schema(example = "5", minimum = "0", maximum = "100",
                description = "Must not exceed totalClaims") Integer independentClaims,
        @Schema(example = "false") Boolean multipleDependentClaims,
        @Min(0) @Max(10000) @Schema(example = "120", minimum = "0", maximum = "10000") Integer specificationSheets,
        @Schema(example = "true", defaultValue = "true") Boolean filedElectronically,
        @Schema(example = "false") Boolean lateFilingSurcharge,
        @Min(0) @Max(5) @Schema(example = "0", minimum = "0", maximum = "5") Integer extensionMonths,
        @Schema(example = "NONE", defaultValue = "NONE") ContinuedExamination continuedExamination,
        @Schema(example = "false", description = "Track One; max 4 independent / 30 total claims, "
                + "not for DESIGN, PLANT or PROVISIONAL") Boolean prioritizedExamination,
        @Schema(example = "2025-03-01", description = "Selects the fee schedule; defaults to today") LocalDate filingDate) {

    public PatentFilingRequest withFilingDate(LocalDate date) {
        return new PatentFilingRequest(applicationType, entitySize, totalClaims, independentClaims,
                multipleDependentClaims, specificationSheets, filedElectronically, lateFilingSurcharge,
                extensionMonths, continuedExamination, prioritizedExamination, date);
    }
}
