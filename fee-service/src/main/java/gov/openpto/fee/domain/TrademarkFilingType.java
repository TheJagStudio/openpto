package gov.openpto.fee.domain;

public enum TrademarkFilingType {
    APPLICATION,
    STATEMENT_OF_USE,
    EXTENSION_SOU,
    SECTION_8,
    SECTION_15,
    SECTION_9_RENEWAL,
    SECTION_8_AND_9;

    /** Only Section 8 and Section 9 filings have a grace period (with a per-class surcharge). */
    public boolean hasGracePeriod() {
        return this == SECTION_8 || this == SECTION_9_RENEWAL || this == SECTION_8_AND_9;
    }
}
