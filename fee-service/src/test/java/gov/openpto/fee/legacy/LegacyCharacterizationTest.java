package gov.openpto.fee.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.openpto.fee.domain.ApplicationType;
import gov.openpto.fee.domain.ContinuedExamination;
import gov.openpto.fee.domain.EntitySize;
import gov.openpto.fee.domain.FeeBreakdown;
import gov.openpto.fee.domain.FeeCodes;
import gov.openpto.fee.domain.FeeRuleViolationException;
import gov.openpto.fee.domain.FeeSchedule;
import gov.openpto.fee.domain.InputGenerators;
import gov.openpto.fee.domain.MaintenanceCalculator;
import gov.openpto.fee.domain.MaintenanceInput;
import gov.openpto.fee.domain.MaintenanceResult;
import gov.openpto.fee.domain.MaintenanceStatus;
import gov.openpto.fee.domain.MaintenanceWindow;
import gov.openpto.fee.domain.PatentFilingCalculator;
import gov.openpto.fee.domain.PatentFilingInput;
import gov.openpto.fee.domain.TestSchedules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.stream.Stream;

import legacy.fees.LegacyFeeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Characterization tests: the extracted fee-service core must charge exactly what the legacy monolith charged
 * (FY2025 rules), to the cent, for every valid input - except for the documented, intentionally fixed defect.
 *
 * <h2>Known legacy defect LEGACY-001 (intentionally fixed)</h2>
 * The monolith computed the application-size fee blocks as {@code (sheets - 100) / 50 + 1}. That is an
 * off-by-one "ceiling": for an exact multiple of 50 sheets over 100 (150, 200, 250, ... sheets) it charged one
 * extra 50-sheet block. The rule is "each additional 50 sheets <em>or fraction thereof</em>", i.e.
 * {@code ceil((sheets - 100) / 50)}: 150 sheets = 1 block, not 2. The new service charges the correct amount;
 * on those inputs the legacy total is exactly one APP_SIZE fee higher, which these tests assert explicitly.
 */
class LegacyCharacterizationTest {

    private static final FeeSchedule FY2025 = TestSchedules.fy2025();
    private final PatentFilingCalculator calculator = new PatentFilingCalculator();
    private final MaintenanceCalculator maintenance = new MaintenanceCalculator();

    // ------------------------------------------------------------------ adapters to the legacy API --

    static double legacyFiling(PatentFilingInput in) {
        int type = switch (in.applicationType()) {
            case UTILITY -> LegacyFeeEngine.UTIL;
            case DESIGN -> LegacyFeeEngine.DESIGN;
            case PLANT -> LegacyFeeEngine.PLANT;
            case PROVISIONAL -> LegacyFeeEngine.PROV;
            case REISSUE -> LegacyFeeEngine.REISSUE;
        };
        int rce = switch (in.continuedExamination()) {
            case NONE -> 0;
            case FIRST -> 1;
            case SUBSEQUENT -> 2;
        };
        return LegacyFeeEngine.calcFilingFee(type, entityCode(in.entitySize()), in.totalClaims(), in.independentClaims(),
                in.multipleDependentClaims(), in.specificationSheets(), in.filedElectronically(),
                in.lateFilingSurcharge(), in.extensionMonths(), rce, in.prioritizedExamination());
    }

    static String entityCode(EntitySize e) {
        return switch (e) {
            case LARGE -> LegacyFeeEngine.LARGE;
            case SMALL -> LegacyFeeEngine.SMALL;
            case MICRO -> LegacyFeeEngine.MICRO;
        };
    }

    static Date legacyDate(LocalDate d) {
        return new GregorianCalendar(d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).getTime();
    }

    static BigDecimal cents(double legacyAmount) {
        return BigDecimal.valueOf(legacyAmount).setScale(2, RoundingMode.HALF_UP);
    }

    /** Inputs on which LEGACY-001 fires: an exact multiple of 50 sheets over 100. */
    static boolean legacy001Applies(PatentFilingInput in) {
        return in.specificationSheets() > 100 && (in.specificationSheets() - 100) % 50 == 0;
    }

    // ------------------------------------------------------------------ comparison --

    /** Compares one input; returns true if the known defect fired (and was verified). */
    private boolean assertEquivalent(PatentFilingInput in) {
        double legacy = legacyFiling(in);
        FeeBreakdown quote;
        try {
            quote = calculator.calculate(FY2025, in);
        } catch (FeeRuleViolationException rejected) {
            assertThat(legacy).as("legacy must reject what the service rejects: %s", in).isEqualTo(LegacyFeeEngine.ERROR);
            return false;
        }
        assertThat(legacy).as("legacy rejected a valid input: %s", in).isNotEqualTo(LegacyFeeEngine.ERROR);
        if (legacy001Applies(in)) {
            BigDecimal oneExtraBlock = FY2025.require(FeeCodes.APP_SIZE).amountFor(in.entitySize());
            assertThat(cents(legacy)).as("LEGACY-001 overcharge for %s", in).isEqualTo(quote.total().add(oneExtraBlock));
            return true;
        }
        assertThat(quote.total()).as("total for %s", in).isEqualTo(cents(legacy));
        return false;
    }

    @Test
    @DisplayName("10,000 seeded random valid inputs: service == legacy to the cent (except LEGACY-001)")
    void randomInputs_matchLegacy() {
        Random rnd = new Random(20250119L);
        int compared = 0;
        int defects = 0;
        for (int i = 0; i < 10_000; i++) {
            if (assertEquivalent(InputGenerators.patentFiling(rnd))) {
                defects++;
            }
            compared++;
        }
        assertThat(compared).isEqualTo(10_000);
        assertThat(defects).as("generator must exercise the defect").isPositive();
    }

    static Stream<PatentFilingInput> boundaryGrid() {
        int[] claims = {0, 1, 3, 4, 20, 21, 30, 31, 500};
        int[] indeps = {0, 3, 4, 5, 100};
        int[] sheets = {0, 100, 101, 150, 151, 200};
        List<PatentFilingInput> grid = new ArrayList<>();
        for (ApplicationType type : ApplicationType.values()) {
            for (EntitySize entity : EntitySize.values()) {
                for (int total : claims) {
                    for (int indep : indeps) {
                        for (int sheet : sheets) {
                            for (int ext : new int[]{0, 5}) {
                                for (ContinuedExamination rce : ContinuedExamination.values()) {
                                    boolean flag = (total + indep + sheet + ext + rce.ordinal()) % 2 == 0;
                                    grid.add(new PatentFilingInput(type, entity, total, indep, flag, sheet, !flag,
                                            flag, ext, rce, total <= 31 && indep <= 5 && flag));
                                }
                            }
                        }
                    }
                }
            }
        }
        return grid.stream();
    }

    @Test
    @DisplayName("Exhaustive boundary grid (incl. invalid combos): identical accept/reject and totals")
    void boundaryGrid_matchesLegacy() {
        long count = boundaryGrid().peek(this::assertEquivalent).count();
        assertThat(count).isEqualTo(5L * 3 * 9 * 5 * 6 * 2 * 3);
    }

    @ParameterizedTest(name = "{0} sheets {1}: legacy {2}, service {3} (LEGACY-001)")
    @CsvSource({
            "150,   LARGE, 2880.00, 2440.00",
            "150,   SMALL, 1152.00, 976.00",
            "150,   MICRO, 576.00,  488.00",
            "200,   LARGE, 3320.00, 2880.00",
            "10000, MICRO, 17912.00, 17824.00",
    })
    @DisplayName("LEGACY-001: exact multiples of 50 sheets over 100 were overcharged by one block - fixed")
    void legacyDefect_sizeFeeOffByOne_isIntentionallyFixed(int sheets, EntitySize entity, String legacyTotal,
                                                           String serviceTotal) {
        PatentFilingInput in = PatentFilingInput.basic(ApplicationType.UTILITY, entity, 0, 0, sheets);
        assertThat(cents(legacyFiling(in))).isEqualTo(new BigDecimal(legacyTotal));
        assertThat(calculator.calculate(FY2025, in).total()).isEqualTo(new BigDecimal(serviceTotal));
    }

    @ParameterizedTest(name = "{0} sheets: legacy and service agree")
    @CsvSource({"100", "101", "149", "151", "199", "201"})
    void legacyDefect_doesNotAffectNonMultiples(int sheets) {
        PatentFilingInput in = PatentFilingInput.basic(ApplicationType.UTILITY, EntitySize.LARGE, 0, 0, sheets);
        assertThat(calculator.calculate(FY2025, in).total()).isEqualTo(cents(legacyFiling(in)));
    }

    @Test
    void legacyRejectsBadEntityCodes_whichTheTypedApiMakesImpossible() {
        assertThat(LegacyFeeEngine.calcFilingFee(1, "X", 0, 0, false, 0, true, false, 0, 0, false)).isEqualTo(-1.0);
        assertThat(LegacyFeeEngine.calcFilingFee(1, null, 0, 0, false, 0, true, false, 0, 0, false)).isEqualTo(-1.0);
        assertThat(LegacyFeeEngine.calcFilingFee(9, "L", 0, 0, false, 0, true, false, 0, 0, false)).isEqualTo(-1.0);
        assertThat(LegacyFeeEngine.calcMaintenance("X", new Date(), new Date())).isNull();
    }

    // ------------------------------------------------------------------ maintenance --

    @Test
    @DisplayName("Maintenance: 5,000 random grant/as-of dates (FY2025 period) match legacy windows exactly")
    void maintenance_randomDates_matchLegacy() {
        Random rnd = new Random(3_5_7_5L);
        for (int i = 0; i < 5_000; i++) {
            LocalDate grant = InputGenerators.grantDate(rnd);
            LocalDate earliest = grant.isAfter(TestSchedules.FY2025_START) ? grant : TestSchedules.FY2025_START;
            LocalDate asOf = earliest.plusDays(rnd.nextInt(15 * 366));
            EntitySize entity = InputGenerators.pick(rnd, EntitySize.values());
            assertMaintenanceEquivalent(entity, grant, asOf);
        }
    }

    static Stream<LocalDate> maintenanceBoundaryDays() {
        LocalDate grant = LocalDate.of(2022, 2, 28);
        List<LocalDate> days = new ArrayList<>();
        for (int months : new int[]{36, 42, 48, 84, 90, 96, 132, 138, 144}) {
            LocalDate edge = grant.plusMonths(months);
            days.add(edge.minusDays(1));
            days.add(edge);
            days.add(edge.plusDays(1));
        }
        return days.stream().filter(d -> !d.isBefore(TestSchedules.FY2025_START));
    }

    @ParameterizedTest(name = "grant 2022-02-28, asOf {0}")
    @MethodSource("maintenanceBoundaryDays")
    void maintenance_boundaryDays_matchLegacy(LocalDate asOf) {
        for (EntitySize entity : EntitySize.values()) {
            assertMaintenanceEquivalent(entity, LocalDate.of(2022, 2, 28), asOf);
        }
    }

    @Test
    void maintenance_leapDayGrant_matchesLegacy() {
        LocalDate grant = LocalDate.of(2024, 2, 29);
        for (LocalDate d = grant.plusMonths(35); d.isBefore(grant.plusMonths(145)); d = d.plusDays(3)) {
            assertMaintenanceEquivalent(EntitySize.SMALL, grant, d);
        }
    }

    @Test
    void maintenance_legacyRejectsAsOfBeforeGrant_likeTheService() {
        LocalDate grant = LocalDate.of(2025, 3, 1);
        assertThat(LegacyFeeEngine.calcMaintenance("L", legacyDate(grant), legacyDate(grant.minusDays(1)))).isNull();
        assertThatThrownBy(() -> maintenance.calculate(FY2025, new MaintenanceInput(EntitySize.LARGE, grant, grant.minusDays(1))))
                .isInstanceOf(FeeRuleViolationException.class);
    }

    @SuppressWarnings("rawtypes")
    private void assertMaintenanceEquivalent(EntitySize entity, LocalDate grant, LocalDate asOf) {
        Vector legacy = LegacyFeeEngine.calcMaintenance(entityCode(entity), legacyDate(grant), legacyDate(asOf));
        MaintenanceResult result = maintenance.calculate(FY2025, new MaintenanceInput(entity, grant, asOf));
        assertThat(legacy).hasSize(3);
        for (int i = 0; i < 3; i++) {
            double[] row = (double[]) legacy.get(i);
            MaintenanceWindow w = result.windows().get(i);
            String ctx = entity + " grant " + grant + " asOf " + asOf + " stage " + w.stage().label();
            assertThat(w.status()).as(ctx).isEqualTo(legacyStatus((int) row[0]));
            assertThat(w.fee()).as(ctx).isEqualTo(cents(row[1]));
            assertThat(w.surcharge()).as(ctx).isEqualTo(cents(row[2]));
            if (row[3] < 0) {
                assertThat(w.totalIfPaidOnAsOfDate()).as(ctx).isNull();
            } else {
                assertThat(w.totalIfPaidOnAsOfDate()).as(ctx).isEqualTo(cents(row[3]));
            }
        }
    }

    private static MaintenanceStatus legacyStatus(int code) {
        return switch (code) {
            case LegacyFeeEngine.ST_NOT_OPEN -> MaintenanceStatus.NOT_YET_OPEN;
            case LegacyFeeEngine.ST_OPEN -> MaintenanceStatus.OPEN;
            case LegacyFeeEngine.ST_GRACE -> MaintenanceStatus.GRACE_PERIOD;
            case LegacyFeeEngine.ST_PASSED -> MaintenanceStatus.PAID_WINDOW_PASSED;
            default -> throw new IllegalArgumentException("legacy status " + code);
        };
    }
}
