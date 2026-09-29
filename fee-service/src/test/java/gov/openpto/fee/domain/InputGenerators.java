package gov.openpto.fee.domain;

import java.time.LocalDate;
import java.util.Random;

/** Seeded random generators of VALID calculator inputs, biased toward rule boundaries. */
public final class InputGenerators {

    private static final int[] CLAIM_EDGES = {0, 1, 3, 4, 5, 19, 20, 21, 22, 29, 30, 31, 100, 499, 500};
    private static final int[] INDEP_EDGES = {0, 1, 2, 3, 4, 5, 99, 100};
    private static final int[] SHEET_EDGES = {0, 1, 99, 100, 101, 149, 150, 151, 199, 200, 201, 250, 251, 9999, 10000};

    private InputGenerators() {
    }

    public static PatentFilingInput patentFiling(Random rnd) {
        ApplicationType type = pick(rnd, ApplicationType.values());
        EntitySize entity = pick(rnd, EntitySize.values());
        int total = rnd.nextInt(3) == 0 ? CLAIM_EDGES[rnd.nextInt(CLAIM_EDGES.length)] : rnd.nextInt(501);
        int indepCandidate = rnd.nextInt(3) == 0 ? INDEP_EDGES[rnd.nextInt(INDEP_EDGES.length)] : rnd.nextInt(101);
        int indep = Math.min(indepCandidate, Math.min(total, 100));
        int sheets = rnd.nextInt(3) == 0 ? SHEET_EDGES[rnd.nextInt(SHEET_EDGES.length)] : rnd.nextInt(10_001);
        boolean trackOne = type.allowsPrioritizedExamination() && indep <= 4 && total <= 30 && rnd.nextBoolean();
        return new PatentFilingInput(type, entity, total, indep, rnd.nextInt(4) == 0, sheets, rnd.nextInt(5) != 0,
                rnd.nextInt(4) == 0, rnd.nextInt(6), pick(rnd, ContinuedExamination.values()), trackOne);
    }

    public static TrademarkFeeInput trademark(Random rnd) {
        return new TrademarkFeeInput(pick(rnd, TrademarkFilingType.values()), 1 + rnd.nextInt(45), rnd.nextBoolean(),
                rnd.nextBoolean(), rnd.nextInt(4) == 0 ? rnd.nextInt(101) : 0, rnd.nextBoolean());
    }

    /** Grant dates 2000..2025 with leap days and month ends over-represented. */
    public static LocalDate grantDate(Random rnd) {
        return switch (rnd.nextInt(6)) {
            case 0 -> LocalDate.of(2000 + 4 * rnd.nextInt(7), 2, 29);
            case 1 -> LocalDate.of(2000 + rnd.nextInt(26), 1 + rnd.nextInt(12), 1).plusMonths(1).minusDays(1);
            default -> LocalDate.of(2000, 1, 1).plusDays(rnd.nextInt(9500));
        };
    }

    public static <T> T pick(Random rnd, T[] values) {
        return values[rnd.nextInt(values.length)];
    }
}
