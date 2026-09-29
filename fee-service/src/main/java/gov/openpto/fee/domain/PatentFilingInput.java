package gov.openpto.fee.domain;

import java.util.Objects;

/** Normalized (defaults applied) patent filing-fee request for the pure calculator. */
public record PatentFilingInput(
        ApplicationType applicationType,
        EntitySize entitySize,
        int totalClaims,
        int independentClaims,
        boolean multipleDependentClaims,
        int specificationSheets,
        boolean filedElectronically,
        boolean lateFilingSurcharge,
        int extensionMonths,
        ContinuedExamination continuedExamination,
        boolean prioritizedExamination) {

    public static final int MAX_TOTAL_CLAIMS = 500;
    public static final int MAX_INDEPENDENT_CLAIMS = 100;
    public static final int MAX_SHEETS = 10_000;
    public static final int MAX_EXTENSION_MONTHS = 5;

    public PatentFilingInput {
        Objects.requireNonNull(applicationType, "applicationType");
        Objects.requireNonNull(entitySize, "entitySize");
        continuedExamination = continuedExamination == null ? ContinuedExamination.NONE : continuedExamination;
    }

    /** Convenience for tests and simple callers: a plain electronic filing with no extras. */
    public static PatentFilingInput basic(ApplicationType type, EntitySize entity, int totalClaims,
                                          int independentClaims, int sheets) {
        return new PatentFilingInput(type, entity, totalClaims, independentClaims, false, sheets, true,
                false, 0, ContinuedExamination.NONE, false);
    }
}
