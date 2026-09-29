package gov.openpto.fee.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Patent filing-fee rules. Pure: no I/O, no framework; the same input and schedule always give the same
 * breakdown.
 *
 * <ul>
 *   <li>basic filing + search + examination per application type (provisional: filing fee only)</li>
 *   <li>utility/reissue only: each claim over 20, each independent claim over 3 (reissue: over the original
 *       count, treated as over 3), multiple-dependent claim once per application</li>
 *   <li>application size fee per additional 50 sheets <em>or fraction</em> over 100</li>
 *   <li>non-electronic filing fee (utility only; micro pays the small rate, stored in the schedule)</li>
 *   <li>late filing surcharge, extension of time (1..5 months), RCE first/subsequent, Track One</li>
 * </ul>
 */
public final class PatentFilingCalculator {

    public static final int CLAIMS_INCLUDED = 20;
    public static final int INDEPENDENT_CLAIMS_INCLUDED = 3;
    public static final int SHEETS_INCLUDED = 100;
    public static final int SHEETS_PER_SIZE_UNIT = 50;
    public static final int TRACK_ONE_MAX_INDEPENDENT = 4;
    public static final int TRACK_ONE_MAX_TOTAL = 30;

    public FeeBreakdown calculate(FeeSchedule schedule, PatentFilingInput in) {
        validate(in);
        ApplicationType type = in.applicationType();
        FeeBreakdown.Builder b = FeeBreakdown.builder(schedule, in.entitySize()).note(FeeNotes.ILLUSTRATIVE);
        if (in.entitySize() != EntitySize.LARGE) {
            b.note(FeeNotes.ENTITY_DISCOUNT);
        }

        chargeBasicFees(b, type);
        chargeClaims(b, in);

        int sizeUnits = applicationSizeUnits(in.specificationSheets());
        if (sizeUnits > 0) {
            b.charge(FeeCodes.APP_SIZE, sizeUnits)
                    .note("Application size fee: " + sizeUnits + " x 50 sheets (or fraction) over 100 sheets.");
        }

        if (!in.filedElectronically()) {
            if (type == ApplicationType.UTILITY) {
                b.charge(FeeCodes.NON_ELECTRONIC, 1);
                if (in.entitySize() == EntitySize.MICRO) {
                    b.note("Non-electronic filing fee: micro entities pay the small-entity rate.");
                }
            } else {
                b.warn("The non-electronic filing fee applies to utility applications only; not charged.");
            }
        }

        if (in.lateFilingSurcharge()) {
            b.charge(FeeCodes.LATE_SURCHARGE, 1);
        }

        if (in.extensionMonths() > 0) {
            if (type == ApplicationType.PROVISIONAL) {
                b.warn("Extensions of time do not apply to provisional applications; not charged.");
            } else {
                b.charge(FeeCodes.extension(in.extensionMonths()), 1);
            }
        }

        if (in.continuedExamination() != ContinuedExamination.NONE) {
            if (type.allowsContinuedExamination()) {
                b.charge(in.continuedExamination() == ContinuedExamination.FIRST
                        ? FeeCodes.RCE_FIRST : FeeCodes.RCE_SUBSEQUENT, 1);
            } else {
                b.warn("Continued examination (RCE) is not available for " + type + " applications; not charged.");
            }
        }

        if (in.prioritizedExamination()) {
            b.charge(FeeCodes.TRACK_ONE, 1)
                    .note("Track One allows at most " + TRACK_ONE_MAX_INDEPENDENT + " independent and "
                            + TRACK_ONE_MAX_TOTAL + " total claims.");
        }
        return b.build();
    }

    /** Number of 50-sheet blocks (or fraction) over 100 sheets: 100 -> 0, 101..150 -> 1, 151..200 -> 2. */
    public static int applicationSizeUnits(int sheets) {
        if (sheets <= SHEETS_INCLUDED) {
            return 0;
        }
        return Math.ceilDiv(sheets - SHEETS_INCLUDED, SHEETS_PER_SIZE_UNIT);
    }

    public static int claimsOver20(int totalClaims) {
        return Math.max(0, totalClaims - CLAIMS_INCLUDED);
    }

    public static int independentOver3(int independentClaims) {
        return Math.max(0, independentClaims - INDEPENDENT_CLAIMS_INCLUDED);
    }

    private static void chargeBasicFees(FeeBreakdown.Builder b, ApplicationType type) {
        switch (type) {
            case UTILITY ->
                    b.charge(FeeCodes.UTIL_FILING, 1).charge(FeeCodes.UTIL_SEARCH, 1).charge(FeeCodes.UTIL_EXAM, 1);
            case DESIGN ->
                    b.charge(FeeCodes.DESIGN_FILING, 1).charge(FeeCodes.DESIGN_SEARCH, 1).charge(FeeCodes.DESIGN_EXAM, 1);
            case PLANT ->
                    b.charge(FeeCodes.PLANT_FILING, 1).charge(FeeCodes.PLANT_SEARCH, 1).charge(FeeCodes.PLANT_EXAM, 1);
            case REISSUE ->
                    b.charge(FeeCodes.REISSUE_FILING, 1).charge(FeeCodes.REISSUE_SEARCH, 1).charge(FeeCodes.REISSUE_EXAM, 1);
            case PROVISIONAL -> b.charge(FeeCodes.PROV_FILING, 1);
        }
    }

    private static void chargeClaims(FeeBreakdown.Builder b, PatentFilingInput in) {
        ApplicationType type = in.applicationType();
        int over20 = claimsOver20(in.totalClaims());
        int indepOver3 = independentOver3(in.independentClaims());
        if (type.chargesExcessClaims()) {
            b.charge(FeeCodes.CLAIM_INDEP_OVER_3, indepOver3)
                    .charge(FeeCodes.CLAIM_OVER_20, over20)
                    .charge(FeeCodes.CLAIM_MULTI_DEP, in.multipleDependentClaims() ? 1 : 0);
            if (type == ApplicationType.REISSUE && indepOver3 > 0) {
                b.note("Reissue: independent claims in excess of the original patent's count are treated as in excess of 3.");
            }
        } else if (over20 > 0 || indepOver3 > 0 || in.multipleDependentClaims()) {
            b.warn("Excess claim fees do not apply to " + type + " applications; not charged.");
        }
    }

    private static void validate(PatentFilingInput in) {
        List<RuleViolation> v = new ArrayList<>();
        range(v, "totalClaims", in.totalClaims(), 0, PatentFilingInput.MAX_TOTAL_CLAIMS);
        range(v, "independentClaims", in.independentClaims(), 0, PatentFilingInput.MAX_INDEPENDENT_CLAIMS);
        range(v, "specificationSheets", in.specificationSheets(), 0, PatentFilingInput.MAX_SHEETS);
        range(v, "extensionMonths", in.extensionMonths(), 0, PatentFilingInput.MAX_EXTENSION_MONTHS);
        if (in.independentClaims() > in.totalClaims()) {
            v.add(new RuleViolation("independentClaims", "must not exceed totalClaims"));
        }
        if (in.prioritizedExamination()) {
            if (!in.applicationType().allowsPrioritizedExamination()) {
                v.add(new RuleViolation("prioritizedExamination",
                        "Track One prioritized examination is not available for " + in.applicationType() + " applications"));
            }
            if (in.independentClaims() > TRACK_ONE_MAX_INDEPENDENT) {
                v.add(new RuleViolation("independentClaims",
                        "Track One allows at most " + TRACK_ONE_MAX_INDEPENDENT + " independent claims"));
            }
            if (in.totalClaims() > TRACK_ONE_MAX_TOTAL) {
                v.add(new RuleViolation("totalClaims",
                        "Track One allows at most " + TRACK_ONE_MAX_TOTAL + " total claims"));
            }
        }
        if (!v.isEmpty()) {
            throw new FeeRuleViolationException(v);
        }
    }

    private static void range(List<RuleViolation> v, String field, int value, int min, int max) {
        if (value < min || value > max) {
            v.add(new RuleViolation(field, "must be between " + min + " and " + max));
        }
    }
}
