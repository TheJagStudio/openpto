package gov.openpto.fee.domain;

/** Patent application types supported by the filing-fee calculator. */
public enum ApplicationType {
    UTILITY,
    DESIGN,
    PLANT,
    PROVISIONAL,
    REISSUE;

    /** Excess-claim and multiple-dependent-claim fees apply only to utility and reissue applications. */
    public boolean chargesExcessClaims() {
        return this == UTILITY || this == REISSUE;
    }

    /** Continued examination (RCE) is not available for design or provisional applications. */
    public boolean allowsContinuedExamination() {
        return this != DESIGN && this != PROVISIONAL;
    }

    /** Track One prioritized examination is not available for design, plant or provisional applications. */
    public boolean allowsPrioritizedExamination() {
        return this == UTILITY || this == REISSUE;
    }
}
