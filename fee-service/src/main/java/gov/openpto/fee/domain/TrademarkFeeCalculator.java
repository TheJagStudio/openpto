package gov.openpto.fee.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Trademark fee rules: every fee is per class. Base-application surcharges (insufficient information,
 * free-form text, each extra 1,000 characters) only exist in schedules that define them; for older
 * schedules they produce a warning and are not charged.
 */
public final class TrademarkFeeCalculator {

    public FeeBreakdown calculate(FeeSchedule schedule, TrademarkFeeInput in) {
        validate(in);
        int classes = in.numberOfClasses();
        TrademarkFilingType type = in.filingType();
        FeeBreakdown.Builder b = FeeBreakdown.builder(schedule, null)
                .note(FeeNotes.ILLUSTRATIVE)
                .note(FeeNotes.TRADEMARK_PER_CLASS);

        switch (type) {
            case APPLICATION -> chargeApplication(b, schedule, in);
            case STATEMENT_OF_USE -> b.charge(FeeCodes.TM_SOU, classes);
            case EXTENSION_SOU -> b.charge(FeeCodes.TM_EXT_SOU, classes);
            case SECTION_8 -> b.charge(FeeCodes.TM_SEC8, classes);
            case SECTION_15 -> b.charge(FeeCodes.TM_SEC15, classes);
            case SECTION_9_RENEWAL -> b.charge(FeeCodes.TM_SEC9, classes);
            case SECTION_8_AND_9 -> b.charge(FeeCodes.TM_SEC8, classes).charge(FeeCodes.TM_SEC9, classes);
        }

        if (type != TrademarkFilingType.APPLICATION
                && (in.insufficientInformation() || in.freeFormTextIds() || in.extraCharacterBlocks() > 0)) {
            b.warn("Base-application surcharges apply only to APPLICATION filings; ignored for " + type + ".");
        }

        if (in.inGracePeriod()) {
            if (!type.hasGracePeriod()) {
                b.warn("There is no grace-period surcharge for " + type + "; ignored.");
            } else {
                if (type != TrademarkFilingType.SECTION_9_RENEWAL) {
                    b.charge(FeeCodes.TM_SEC8_GRACE, classes);
                }
                if (type != TrademarkFilingType.SECTION_8) {
                    b.charge(FeeCodes.TM_SEC9_GRACE, classes);
                }
            }
        }
        return b.build();
    }

    private static void chargeApplication(FeeBreakdown.Builder b, FeeSchedule schedule, TrademarkFeeInput in) {
        int classes = in.numberOfClasses();
        b.charge(FeeCodes.TM_BASE_APP, classes);
        String missing = " did not exist in fee schedule " + schedule.code() + "; not charged.";
        b.chargeIfDefined(FeeCodes.TM_INSUFFICIENT_INFO, in.insufficientInformation() ? classes : 0,
                "The insufficient-information surcharge" + missing);
        b.chargeIfDefined(FeeCodes.TM_FREE_FORM, in.freeFormTextIds() ? classes : 0,
                "The free-form text identification surcharge" + missing);
        b.chargeIfDefined(FeeCodes.TM_EXTRA_CHARS, in.extraCharacterBlocks() * classes,
                "The additional-characters surcharge" + missing);
        if (in.extraCharacterBlocks() > 0 && schedule.has(FeeCodes.TM_EXTRA_CHARS)) {
            b.note("Additional characters: " + in.extraCharacterBlocks() + " block(s) of 1,000 characters x "
                    + classes + " class(es).");
        }
    }

    private static void validate(TrademarkFeeInput in) {
        List<RuleViolation> v = new ArrayList<>();
        if (in.numberOfClasses() < 1 || in.numberOfClasses() > TrademarkFeeInput.MAX_CLASSES) {
            v.add(new RuleViolation("numberOfClasses", "must be between 1 and " + TrademarkFeeInput.MAX_CLASSES));
        }
        if (in.extraCharacterBlocks() < 0 || in.extraCharacterBlocks() > TrademarkFeeInput.MAX_CHARACTER_BLOCKS) {
            v.add(new RuleViolation("extraCharacterBlocks",
                    "must be between 0 and " + TrademarkFeeInput.MAX_CHARACTER_BLOCKS));
        }
        if (!v.isEmpty()) {
            throw new FeeRuleViolationException(v);
        }
    }
}
