package gov.openpto.fee.domain;

import static gov.openpto.fee.domain.ApplicationType.DESIGN;
import static gov.openpto.fee.domain.ApplicationType.PLANT;
import static gov.openpto.fee.domain.ApplicationType.PROVISIONAL;
import static gov.openpto.fee.domain.ApplicationType.REISSUE;
import static gov.openpto.fee.domain.ApplicationType.UTILITY;
import static gov.openpto.fee.domain.EntitySize.LARGE;
import static gov.openpto.fee.domain.EntitySize.MICRO;
import static gov.openpto.fee.domain.EntitySize.SMALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class PatentFilingCalculatorTest {

    private final PatentFilingCalculator calculator = new PatentFilingCalculator();
    private static final FeeSchedule FY2025 = TestSchedules.fy2025();
    private static final FeeSchedule FY2023 = TestSchedules.fy2023();

    private static PatentFilingInput basic(ApplicationType type, EntitySize entity) {
        return PatentFilingInput.basic(type, entity, 0, 0, 0);
    }

    private static PatentFilingInput with(ApplicationType type, EntitySize entity, int total, int indep, boolean multiDep,
                                          int sheets, boolean electronic, boolean late, int ext,
                                          ContinuedExamination rce, boolean trackOne) {
        return new PatentFilingInput(type, entity, total, indep, multiDep, sheets, electronic, late, ext, rce, trackOne);
    }

    private static BigDecimal usd(String amount) {
        return new BigDecimal(amount).setScale(2);
    }

    @Nested
    class BasicFees {

        @ParameterizedTest(name = "FY2025 {0} {1} -> {2}")
        @CsvSource({
                "UTILITY,     LARGE, 2000.00", "UTILITY,     SMALL, 800.00", "UTILITY,     MICRO, 400.00",
                "DESIGN,      LARGE, 1100.00", "DESIGN,      SMALL, 440.00", "DESIGN,      MICRO, 220.00",
                "PLANT,       LARGE, 1530.00", "PLANT,       SMALL, 612.00", "PLANT,       MICRO, 306.00",
                "PROVISIONAL, LARGE, 325.00", "PROVISIONAL, SMALL, 130.00", "PROVISIONAL, MICRO, 65.00",
                "REISSUE,     LARGE, 3980.00", "REISSUE,     SMALL, 1592.00", "REISSUE,     MICRO, 796.00",
        })
        void basicFees_fy2025(ApplicationType type, EntitySize entity, String expected) {
            FeeBreakdown quote = calculator.calculate(FY2025, basic(type, entity));
            assertThat(quote.total()).isEqualTo(usd(expected));
            assertThat(quote.scheduleCode()).isEqualTo("FY2025");
            assertThat(quote.entitySize()).isEqualTo(entity);
        }

        @ParameterizedTest(name = "FY2023 {0} {1} -> {2}")
        @CsvSource({
                "UTILITY,     LARGE, 1820.00", "UTILITY,     SMALL, 728.00", "UTILITY,     MICRO, 364.00",
                "DESIGN,      LARGE, 1080.00", "DESIGN,      SMALL, 432.00", "DESIGN,      MICRO, 216.00",
                "PLANT,       LARGE, 1420.00", "PLANT,       SMALL, 568.00", "PLANT,       MICRO, 284.00",
                "PROVISIONAL, LARGE, 300.00", "PROVISIONAL, SMALL, 120.00", "PROVISIONAL, MICRO, 60.00",
                "REISSUE,     LARGE, 3500.00", "REISSUE,     SMALL, 1400.00", "REISSUE,     MICRO, 700.00",
        })
        void basicFees_fy2023(ApplicationType type, EntitySize entity, String expected) {
            assertThat(calculator.calculate(FY2023, basic(type, entity)).total()).isEqualTo(usd(expected));
        }

        @Test
        void provisional_chargesFilingFeeOnly() {
            FeeBreakdown quote = calculator.calculate(FY2025, basic(PROVISIONAL, LARGE));
            assertThat(quote.lineItems()).extracting(LineItem::feeCode).containsExactly(FeeCodes.PROV_FILING);
        }

        @Test
        void utility_itemizesFilingSearchExamination() {
            FeeBreakdown quote = calculator.calculate(FY2025, basic(UTILITY, SMALL));
            assertThat(quote.lineItems()).extracting(LineItem::feeCode)
                    .containsExactly(FeeCodes.UTIL_FILING, FeeCodes.UTIL_SEARCH, FeeCodes.UTIL_EXAM);
            assertThat(quote.subtotals()).extracting(Subtotal::group).containsExactly("Filing", "Search", "Examination");
            assertThat(quote.notes()).contains(FeeNotes.ILLUSTRATIVE, FeeNotes.ENTITY_DISCOUNT);
        }

        @Test
        void largeEntity_hasNoDiscountNote() {
            assertThat(calculator.calculate(FY2025, basic(UTILITY, LARGE)).notes()).doesNotContain(FeeNotes.ENTITY_DISCOUNT);
        }
    }

    /** Every patent fee code (except maintenance) x every entity size x both schedules. */
    @Nested
    class EveryFeeCode {

        static Stream<Arguments> feeCodes() {
            List<Arguments> args = new ArrayList<>();
            for (FeeSchedule schedule : TestSchedules.all()) {
                for (FeeRate rate : schedule.itemsIn(FeeCategory.PATENT)) {
                    if (rate.feeCode().startsWith("MAINT_")) {
                        continue;
                    }
                    for (EntitySize entity : EntitySize.values()) {
                        args.add(Arguments.of(schedule.code(), rate.feeCode(), entity));
                    }
                }
            }
            return args.stream();
        }

        @ParameterizedTest(name = "{0} {1} {2}")
        @MethodSource("feeCodes")
        void feeCode_isChargedAtScheduleAmountForEntity(String scheduleCode, String feeCode, EntitySize entity) {
            FeeSchedule schedule = scheduleCode.equals("FY2025") ? FY2025 : FY2023;
            FeeBreakdown quote = calculator.calculate(schedule, triggering(feeCode, entity));
            LineItem line = quote.lineItems().stream().filter(l -> l.feeCode().equals(feeCode)).findFirst().orElseThrow();
            assertThat(line.quantity()).isEqualTo(1);
            assertThat(line.unitAmount()).isEqualTo(schedule.require(feeCode).amountFor(entity));
            assertThat(line.amount()).isEqualTo(line.unitAmount());
            assertThat(line.amount().scale()).isEqualTo(2);
        }

        /** Smallest input that charges exactly one unit of the fee. */
        private PatentFilingInput triggering(String code, EntitySize e) {
            ContinuedExamination none = ContinuedExamination.NONE;
            if (code.startsWith("UTIL_")) {
                return basic(UTILITY, e);
            } else if (code.startsWith("DESIGN_")) {
                return basic(DESIGN, e);
            } else if (code.startsWith("PLANT_")) {
                return basic(PLANT, e);
            } else if (code.startsWith("REISSUE_")) {
                return basic(REISSUE, e);
            } else if (code.startsWith("EXT_")) {
                return with(UTILITY, e, 0, 0, false, 0, true, false, code.charAt(4) - '0', none, false);
            }
            return switch (code) {
                case FeeCodes.PROV_FILING -> basic(PROVISIONAL, e);
                case FeeCodes.CLAIM_INDEP_OVER_3 -> PatentFilingInput.basic(UTILITY, e, 4, 4, 0);
                case FeeCodes.CLAIM_OVER_20 -> PatentFilingInput.basic(UTILITY, e, 21, 1, 0);
                case FeeCodes.CLAIM_MULTI_DEP -> with(UTILITY, e, 3, 1, true, 0, true, false, 0, none, false);
                case FeeCodes.APP_SIZE -> PatentFilingInput.basic(UTILITY, e, 0, 0, 101);
                case FeeCodes.NON_ELECTRONIC -> with(UTILITY, e, 0, 0, false, 0, false, false, 0, none, false);
                case FeeCodes.LATE_SURCHARGE -> with(UTILITY, e, 0, 0, false, 0, true, true, 0, none, false);
                case FeeCodes.RCE_FIRST ->
                        with(UTILITY, e, 0, 0, false, 0, true, false, 0, ContinuedExamination.FIRST, false);
                case FeeCodes.RCE_SUBSEQUENT ->
                        with(UTILITY, e, 0, 0, false, 0, true, false, 0, ContinuedExamination.SUBSEQUENT, false);
                case FeeCodes.TRACK_ONE -> with(UTILITY, e, 0, 0, false, 0, true, false, 0, none, true);
                default -> throw new IllegalArgumentException("No trigger for " + code);
            };
        }
    }

    @Nested
    class ExcessClaims {

        @ParameterizedTest(name = "{0} total claims -> {1} over 20")
        @CsvSource({"0,0", "1,0", "19,0", "20,0", "21,1", "22,2", "30,10", "100,80", "500,480"})
        void claimsOver20_boundaries(int totalClaims, int expectedQuantity) {
            FeeBreakdown quote = calculator.calculate(FY2025, PatentFilingInput.basic(UTILITY, LARGE, totalClaims, 0, 0));
            assertThat(quantityOf(quote, FeeCodes.CLAIM_OVER_20)).isEqualTo(expectedQuantity);
            assertThat(quote.amountOf(FeeCodes.CLAIM_OVER_20)).isEqualTo(usd("200").multiply(BigDecimal.valueOf(expectedQuantity)).setScale(2));
            assertThat(PatentFilingCalculator.claimsOver20(totalClaims)).isEqualTo(expectedQuantity);
        }

        @ParameterizedTest(name = "{0} independent -> {1} over 3")
        @CsvSource({"0,0", "2,0", "3,0", "4,1", "5,2", "100,97"})
        void independentOver3_boundaries(int indep, int expectedQuantity) {
            FeeBreakdown quote = calculator.calculate(FY2025, PatentFilingInput.basic(UTILITY, MICRO, 100, indep, 0));
            assertThat(quantityOf(quote, FeeCodes.CLAIM_INDEP_OVER_3)).isEqualTo(expectedQuantity);
            assertThat(quote.amountOf(FeeCodes.CLAIM_INDEP_OVER_3)).isEqualTo(usd("120").multiply(BigDecimal.valueOf(expectedQuantity)).setScale(2));
        }

        @ParameterizedTest(name = "{0} {1}: 25 claims / 5 independent -> {2}")
        @CsvSource({
                "UTILITY, LARGE, 4200.00", "UTILITY, SMALL, 1680.00", "UTILITY, MICRO, 840.00",
                "REISSUE, LARGE, 6180.00", "REISSUE, SMALL, 2472.00", "REISSUE, MICRO, 1236.00",
        })
        void excessClaims_combined(ApplicationType type, EntitySize entity, String expected) {
            FeeBreakdown quote = calculator.calculate(FY2025, PatentFilingInput.basic(type, entity, 25, 5, 0));
            assertThat(quote.total()).isEqualTo(usd(expected));
            assertThat(quote.subtotals()).anySatisfy(s -> assertThat(s.group()).isEqualTo("Excess claims"));
        }

        @Test
        void reissue_excessIndependent_addsNote() {
            FeeBreakdown quote = calculator.calculate(FY2025, PatentFilingInput.basic(REISSUE, LARGE, 10, 4, 0));
            assertThat(quote.notes()).anyMatch(n -> n.startsWith("Reissue:"));
        }

        @Test
        void multipleDependent_chargedOncePerApplication() {
            FeeBreakdown quote = calculator.calculate(FY2025,
                    with(UTILITY, SMALL, 40, 6, true, 0, true, false, 0, ContinuedExamination.NONE, false));
            assertThat(quantityOf(quote, FeeCodes.CLAIM_MULTI_DEP)).isEqualTo(1);
            assertThat(quote.amountOf(FeeCodes.CLAIM_MULTI_DEP)).isEqualTo(usd("370"));
        }

        @ParameterizedTest
        @EnumSource(value = ApplicationType.class, names = {"DESIGN", "PLANT", "PROVISIONAL"})
        void nonUtility_excessClaims_warnedNotCharged(ApplicationType type) {
            FeeBreakdown quote = calculator.calculate(FY2025,
                    with(type, LARGE, 30, 5, true, 0, true, false, 0, ContinuedExamination.NONE, false));
            assertThat(quote.lineItems()).noneMatch(l -> l.group().equals("Excess claims"));
            assertThat(quote.warnings()).anyMatch(w -> w.startsWith("Excess claim fees do not apply"));
        }

        @Test
        void design_withinLimits_noWarning() {
            assertThat(calculator.calculate(FY2025, PatentFilingInput.basic(DESIGN, LARGE, 1, 1, 0)).warnings()).isEmpty();
        }
    }

    @Nested
    class ApplicationSize {

        @ParameterizedTest(name = "{0} sheets -> {1} size units")
        @CsvSource({"0,0", "1,0", "99,0", "100,0", "101,1", "149,1", "150,1", "151,2", "199,2", "200,2", "201,3",
                "250,3", "251,4", "10000,198"})
        void sizeUnits_boundaries(int sheets, int expectedUnits) {
            assertThat(PatentFilingCalculator.applicationSizeUnits(sheets)).isEqualTo(expectedUnits);
            FeeBreakdown quote = calculator.calculate(FY2025, PatentFilingInput.basic(UTILITY, SMALL, 0, 0, sheets));
            assertThat(quantityOf(quote, FeeCodes.APP_SIZE)).isEqualTo(expectedUnits);
            assertThat(quote.amountOf(FeeCodes.APP_SIZE)).isEqualTo(usd("176").multiply(BigDecimal.valueOf(expectedUnits)).setScale(2));
        }

        @ParameterizedTest
        @EnumSource(ApplicationType.class)
        void sizeFee_appliesToEveryApplicationType(ApplicationType type) {
            FeeBreakdown quote = calculator.calculate(FY2025, PatentFilingInput.basic(type, LARGE, 0, 0, 151));
            assertThat(quote.amountOf(FeeCodes.APP_SIZE)).isEqualTo(usd("880"));
            assertThat(quote.notes()).anyMatch(n -> n.startsWith("Application size fee: 2 x"));
        }
    }

    @Nested
    class SurchargesExtensionsRce {

        @ParameterizedTest(name = "non-electronic {0} -> {1}")
        @CsvSource({"LARGE, 400.00", "SMALL, 200.00", "MICRO, 200.00"})
        void nonElectronic_utility_microPaysSmallRate(EntitySize entity, String expected) {
            FeeBreakdown quote = calculator.calculate(FY2025,
                    with(UTILITY, entity, 0, 0, false, 0, false, false, 0, ContinuedExamination.NONE, false));
            assertThat(quote.amountOf(FeeCodes.NON_ELECTRONIC)).isEqualTo(usd(expected));
            assertThat(quote.notes().stream().anyMatch(n -> n.contains("micro entities pay the small-entity rate")))
                    .isEqualTo(entity == MICRO);
        }

        @ParameterizedTest
        @EnumSource(value = ApplicationType.class, names = {"DESIGN", "PLANT", "PROVISIONAL", "REISSUE"})
        void nonElectronic_nonUtility_warnedNotCharged(ApplicationType type) {
            FeeBreakdown quote = calculator.calculate(FY2025,
                    with(type, LARGE, 0, 0, false, 0, false, false, 0, ContinuedExamination.NONE, false));
            assertThat(quote.amountOf(FeeCodes.NON_ELECTRONIC)).isEqualByComparingTo("0");
            assertThat(quote.warnings()).anyMatch(w -> w.contains("utility applications only"));
        }

        @ParameterizedTest(name = "{0} month(s) {1} -> {2}")
        @CsvSource({
                "0, LARGE, 0.00", "0, SMALL, 0.00", "0, MICRO, 0.00",
                "1, LARGE, 235.00", "1, SMALL, 94.00", "1, MICRO, 47.00",
                "2, LARGE, 530.00", "2, SMALL, 212.00", "2, MICRO, 106.00",
                "3, LARGE, 1260.00", "3, SMALL, 504.00", "3, MICRO, 252.00",
                "4, LARGE, 2010.00", "4, SMALL, 804.00", "4, MICRO, 402.00",
                "5, LARGE, 2760.00", "5, SMALL, 1104.00", "5, MICRO, 552.00",
        })
        void extensionOfTime_byMonths(int months, EntitySize entity, String expected) {
            FeeBreakdown quote = calculator.calculate(FY2025,
                    with(UTILITY, entity, 0, 0, false, 0, true, false, months, ContinuedExamination.NONE, false));
            BigDecimal base = calculator.calculate(FY2025, basic(UTILITY, entity)).total();
            assertThat(quote.total().subtract(base)).isEqualTo(usd(expected));
            assertThat(quote.lineItems().stream().filter(l -> l.feeCode().startsWith("EXT_")).count())
                    .isEqualTo(months == 0 ? 0 : 1);
        }

        @Test
        void extension_provisional_warnedNotCharged() {
            FeeBreakdown quote = calculator.calculate(FY2025,
                    with(PROVISIONAL, LARGE, 0, 0, false, 0, true, false, 3, ContinuedExamination.NONE, false));
            assertThat(quote.total()).isEqualTo(usd("325"));
            assertThat(quote.warnings()).anyMatch(w -> w.startsWith("Extensions of time do not apply"));
        }

        @Test
        void extensionFeeCode_rejectsOutOfRange() {
            assertThatThrownBy(() -> FeeCodes.extension(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FeeCodes.extension(6)).isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest(name = "late surcharge {0} -> {1}")
        @CsvSource({"LARGE, 160.00", "SMALL, 64.00", "MICRO, 32.00"})
        void lateFilingSurcharge(EntitySize entity, String expected) {
            FeeBreakdown quote = calculator.calculate(FY2025,
                    with(DESIGN, entity, 0, 0, false, 0, true, true, 0, ContinuedExamination.NONE, false));
            assertThat(quote.amountOf(FeeCodes.LATE_SURCHARGE)).isEqualTo(usd(expected));
        }

        @ParameterizedTest(name = "RCE {0} {1} {2} -> {3}")
        @CsvSource({
                "UTILITY, FIRST,      LARGE, 1500.00", "UTILITY, SUBSEQUENT, LARGE, 2860.00",
                "UTILITY, FIRST,      SMALL, 600.00", "UTILITY, SUBSEQUENT, SMALL, 1144.00",
                "PLANT,   FIRST,      MICRO, 300.00", "REISSUE, SUBSEQUENT, MICRO, 572.00",
        })
        void continuedExamination(ApplicationType type, ContinuedExamination rce, EntitySize entity, String expected) {
            FeeBreakdown quote = calculator.calculate(FY2025, with(type, entity, 0, 0, false, 0, true, false, 0, rce, false));
            String code = rce == ContinuedExamination.FIRST ? FeeCodes.RCE_FIRST : FeeCodes.RCE_SUBSEQUENT;
            assertThat(quote.amountOf(code)).isEqualTo(usd(expected));
        }

        @ParameterizedTest
        @EnumSource(value = ApplicationType.class, names = {"DESIGN", "PROVISIONAL"})
        void continuedExamination_notAvailable_warnedNotCharged(ApplicationType type) {
            FeeBreakdown quote = calculator.calculate(FY2025,
                    with(type, LARGE, 0, 0, false, 0, true, false, 0, ContinuedExamination.FIRST, false));
            assertThat(quote.amountOf(FeeCodes.RCE_FIRST)).isEqualByComparingTo("0");
            assertThat(quote.warnings()).anyMatch(w -> w.contains("(RCE) is not available"));
        }
    }

    @Nested
    class TrackOne {

        @ParameterizedTest(name = "Track One {0} {1}/{2} claims ok")
        @CsvSource({"UTILITY, 30, 4", "UTILITY, 0, 0", "REISSUE, 30, 4", "UTILITY, 20, 3"})
        void trackOne_withinLimits_charged(ApplicationType type, int total, int indep) {
            FeeBreakdown quote = calculator.calculate(FY2025,
                    with(type, SMALL, total, indep, false, 0, true, false, 0, ContinuedExamination.NONE, true));
            assertThat(quote.amountOf(FeeCodes.TRACK_ONE)).isEqualTo(usd("1806"));
            assertThat(quote.notes()).anyMatch(n -> n.startsWith("Track One allows at most 4 independent"));
        }

        @ParameterizedTest(name = "Track One {0} {1}/{2} -> 400 on {3}")
        @CsvSource({
                "UTILITY,     31, 4, totalClaims",
                "UTILITY,     30, 5, independentClaims",
                "DESIGN,      1,  1, prioritizedExamination",
                "PLANT,       1,  1, prioritizedExamination",
                "PROVISIONAL, 0,  0, prioritizedExamination",
        })
        void trackOne_violations(ApplicationType type, int total, int indep, String field) {
            PatentFilingInput in = with(type, LARGE, total, indep, false, 0, true, false, 0, ContinuedExamination.NONE, true);
            assertThatThrownBy(() -> calculator.calculate(FY2025, in))
                    .isInstanceOfSatisfying(FeeRuleViolationException.class, ex ->
                            assertThat(ex.violations()).extracting(RuleViolation::field).contains(field));
        }

        @Test
        void trackOne_bothLimitsExceeded_reportsBothFields() {
            PatentFilingInput in = with(UTILITY, LARGE, 40, 6, false, 0, true, false, 0, ContinuedExamination.NONE, true);
            assertThatThrownBy(() -> calculator.calculate(FY2025, in))
                    .isInstanceOfSatisfying(FeeRuleViolationException.class, ex -> {
                        assertThat(ex.violations()).extracting(RuleViolation::field)
                                .containsExactlyInAnyOrder("independentClaims", "totalClaims");
                        assertThat(ex.getMessage()).contains("4 independent").contains("30 total");
                    });
        }
    }

    @Nested
    class Validation {

        @Test
        void independentClaims_exceedingTotal_isRejected() {
            assertThatThrownBy(() -> calculator.calculate(FY2025, PatentFilingInput.basic(UTILITY, LARGE, 3, 4, 0)))
                    .isInstanceOfSatisfying(FeeRuleViolationException.class, ex ->
                            assertThat(ex.violations()).containsExactly(
                                    new RuleViolation("independentClaims", "must not exceed totalClaims")));
        }

        @Test
        void independentClaims_equalToTotal_isAccepted() {
            assertThat(calculator.calculate(FY2025, PatentFilingInput.basic(UTILITY, LARGE, 4, 4, 0)).total())
                    .isEqualTo(usd("2600"));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "totalClaims,         501, 0,   0,     0",
                "totalClaims,         -1,  0,   0,     0",
                "independentClaims,   500, 101, 0,     0",
                "specificationSheets, 0,   0,   10001, 0",
                "extensionMonths,     0,   0,   0,     6",
        })
        void outOfRange_isRejected(String field, int total, int indep, int sheets, int ext) {
            PatentFilingInput in = with(UTILITY, LARGE, total, indep, false, sheets, true, false, ext,
                    ContinuedExamination.NONE, false);
            assertThatThrownBy(() -> calculator.calculate(FY2025, in))
                    .isInstanceOfSatisfying(FeeRuleViolationException.class, ex ->
                            assertThat(ex.violations()).extracting(RuleViolation::field).contains(field));
        }

        @Test
        void nullContinuedExamination_defaultsToNone() {
            PatentFilingInput in = new PatentFilingInput(UTILITY, LARGE, 0, 0, false, 0, true, false, 0, null, false);
            assertThat(in.continuedExamination()).isEqualTo(ContinuedExamination.NONE);
        }

        @Test
        void missingFeeInSchedule_isAConfigurationError() {
            FeeSchedule empty = new FeeSchedule(9L, "EMPTY", "empty", TestSchedules.FY2025_START, null, List.of());
            assertThatThrownBy(() -> calculator.calculate(empty, basic(UTILITY, LARGE)))
                    .isInstanceOfSatisfying(MissingFeeException.class, ex -> {
                        assertThat(ex.scheduleCode()).isEqualTo("EMPTY");
                        assertThat(ex.feeCode()).isEqualTo(FeeCodes.UTIL_FILING);
                    });
        }
    }

    /** Property-style invariants over thousands of seeded random valid inputs. */
    @Nested
    class Invariants {

        private static final int CASES = 3000;

        @Test
        void totalEqualsSumOfLineItems_andSubtotals() {
            Random rnd = new Random(42);
            for (int i = 0; i < CASES; i++) {
                PatentFilingInput in = InputGenerators.patentFiling(rnd);
                FeeBreakdown q = calculator.calculate(FY2025, in);
                BigDecimal lines = q.lineItems().stream().map(LineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal subtotals = q.subtotals().stream().map(Subtotal::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(q.total()).as("%s", in).isEqualByComparingTo(lines).isEqualByComparingTo(subtotals);
                assertThat(q.total().scale()).isEqualTo(2);
                q.lineItems().forEach(l -> assertThat(l.amount())
                        .isEqualTo(l.unitAmount().multiply(BigDecimal.valueOf(l.quantity())).setScale(2)));
            }
        }

        @Test
        void microLessOrEqualSmallLessOrEqualLarge() {
            Random rnd = new Random(7);
            for (int i = 0; i < CASES; i++) {
                PatentFilingInput in = InputGenerators.patentFiling(rnd);
                for (FeeSchedule schedule : TestSchedules.all()) {
                    BigDecimal large = calculator.calculate(schedule, withEntity(in, LARGE)).total();
                    BigDecimal small = calculator.calculate(schedule, withEntity(in, SMALL)).total();
                    BigDecimal micro = calculator.calculate(schedule, withEntity(in, MICRO)).total();
                    assertThat(micro).as("%s", in).isLessThanOrEqualTo(small);
                    assertThat(small).as("%s", in).isLessThanOrEqualTo(large);
                }
            }
        }

        @Test
        void monotonicInTotalClaimsAndSheets() {
            Random rnd = new Random(99);
            for (int i = 0; i < CASES; i++) {
                PatentFilingInput in = InputGenerators.patentFiling(rnd);
                if (in.totalClaims() < PatentFilingInput.MAX_TOTAL_CLAIMS && !in.prioritizedExamination()) {
                    PatentFilingInput more = new PatentFilingInput(in.applicationType(), in.entitySize(),
                            in.totalClaims() + 1, in.independentClaims(), in.multipleDependentClaims(),
                            in.specificationSheets(), in.filedElectronically(), in.lateFilingSurcharge(),
                            in.extensionMonths(), in.continuedExamination(), false);
                    assertThat(calculator.calculate(FY2025, more).total()).as("%s", in)
                            .isGreaterThanOrEqualTo(calculator.calculate(FY2025, in).total());
                }
                if (in.specificationSheets() < PatentFilingInput.MAX_SHEETS) {
                    PatentFilingInput bigger = new PatentFilingInput(in.applicationType(), in.entitySize(),
                            in.totalClaims(), in.independentClaims(), in.multipleDependentClaims(),
                            in.specificationSheets() + 1, in.filedElectronically(), in.lateFilingSurcharge(),
                            in.extensionMonths(), in.continuedExamination(), in.prioritizedExamination());
                    assertThat(calculator.calculate(FY2025, bigger).total()).as("%s", in)
                            .isGreaterThanOrEqualTo(calculator.calculate(FY2025, in).total());
                }
            }
        }

        @Test
        void deterministic_sameInputSameResult() {
            Random rnd = new Random(1);
            for (int i = 0; i < 200; i++) {
                PatentFilingInput in = InputGenerators.patentFiling(rnd);
                assertThat(calculator.calculate(FY2025, in)).isEqualTo(calculator.calculate(FY2025, in));
            }
        }

        private static PatentFilingInput withEntity(PatentFilingInput in, EntitySize e) {
            return new PatentFilingInput(in.applicationType(), e, in.totalClaims(), in.independentClaims(),
                    in.multipleDependentClaims(), in.specificationSheets(), in.filedElectronically(),
                    in.lateFilingSurcharge(), in.extensionMonths(), in.continuedExamination(), in.prioritizedExamination());
        }
    }

    @Test
    void fullScenario_everyOptionAtOnce_smallEntity() {
        // 800 base + 5x80 claims + 2x240 indep + 370 multi-dep + 2x176 size + 200 paper + 64 late + 504 ext(3)
        // + 600 RCE = 3770
        FeeBreakdown quote = calculator.calculate(FY2025,
                with(UTILITY, SMALL, 25, 5, true, 151, false, true, 3, ContinuedExamination.FIRST, false));
        assertThat(quote.total()).isEqualTo(usd("3770"));
        assertThat(quote.subtotals()).extracting(Subtotal::group).containsExactly("Filing", "Search", "Examination",
                "Excess claims", "Application size", "Surcharges", "Extensions of time", "Continued examination");
    }

    private static int quantityOf(FeeBreakdown quote, String code) {
        return quote.lineItems().stream().filter(l -> l.feeCode().equals(code)).mapToInt(LineItem::quantity).sum();
    }

    static Stream<ApplicationType> types() {
        return Stream.of(ApplicationType.values());
    }
}
