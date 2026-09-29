package gov.openpto.fee.domain;

import java.util.Objects;

/** Normalized trademark fee request for the pure calculator. */
public record TrademarkFeeInput(
        TrademarkFilingType filingType,
        int numberOfClasses,
        boolean insufficientInformation,
        boolean freeFormTextIds,
        int extraCharacterBlocks,
        boolean inGracePeriod) {

    public static final int MAX_CLASSES = 45;
    public static final int MAX_CHARACTER_BLOCKS = 100;

    public TrademarkFeeInput {
        Objects.requireNonNull(filingType, "filingType");
    }

    public static TrademarkFeeInput of(TrademarkFilingType type, int classes) {
        return new TrademarkFeeInput(type, classes, false, false, 0, false);
    }
}
