package gov.openpto.fee.domain;

/** How a fee item's quantity is counted. */
public enum FeeUnit {
    EACH,
    PER_CLAIM,
    PER_CLASS,
    PER_50_SHEETS,
    PER_MONTH
}
