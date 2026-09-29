package gov.openpto.fee.domain;

import static gov.openpto.fee.domain.TrademarkFilingType.APPLICATION;
import static gov.openpto.fee.domain.TrademarkFilingType.SECTION_15;
import static gov.openpto.fee.domain.TrademarkFilingType.SECTION_8;
import static gov.openpto.fee.domain.TrademarkFilingType.SECTION_8_AND_9;
import static gov.openpto.fee.domain.TrademarkFilingType.SECTION_9_RENEWAL;
import static gov.openpto.fee.domain.TrademarkFilingType.STATEMENT_OF_USE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class TrademarkFeeCalculatorTest {

    private final TrademarkFeeCalculator calculator = new TrademarkFeeCalculator();
    private static final FeeSchedule FY2025 = TestSchedules.fy2025();
    private static final FeeSchedule FY2023 = TestSchedules.fy2023();

    private static BigDecimal usd(String v) {
        return new BigDecimal(v).setScale(2);
    }

    @ParameterizedTest(name = "FY2025 {0} x {1} classes -> {2}")
    @CsvSource({
            "APPLICATION,       1,  350.00", "APPLICATION,       3,  1050.00", "APPLICATION,      45, 15750.00",
            "STATEMENT_OF_USE,  1,  150.00", "STATEMENT_OF_USE,  3,  450.00",
            "EXTENSION_SOU,     1,  125.00", "EXTENSION_SOU,     3,  375.00",
            "SECTION_8,         1,  325.00", "SECTION_8,         3,  975.00",
            "SECTION_15,        1,  250.00", "SECTION_15,        3,  750.00",
            "SECTION_9_RENEWAL, 1,  325.00", "SECTION_9_RENEWAL, 3,  975.00",
            "SECTION_8_AND_9,   1,  650.00", "SECTION_8_AND_9,   3,  1950.00",
    })
    void perClassFees_fy2025(TrademarkFilingType type, int classes, String expected) {
        FeeBreakdown quote = calculator.calculate(FY2025, TrademarkFeeInput.of(type, classes));
        assertThat(quote.total()).isEqualTo(usd(expected));
        assertThat(quote.entitySize()).isNull();
        assertThat(quote.notes()).contains(FeeNotes.TRADEMARK_PER_CLASS);
        assertThat(quote.lineItems()).allSatisfy(l -> assertThat(l.quantity()).isEqualTo(classes));
    }

    @ParameterizedTest(name = "FY2023 {0} x {1} classes -> {2}")
    @CsvSource({
            "APPLICATION,       3, 1050.00", "STATEMENT_OF_USE,  3, 300.00", "EXTENSION_SOU,     3, 375.00",
            "SECTION_8,         3, 675.00", "SECTION_15,        3, 600.00", "SECTION_9_RENEWAL, 3, 900.00",
            "SECTION_8_AND_9,   3, 1575.00",
    })
    void perClassFees_fy2023(TrademarkFilingType type, int classes, String expected) {
        assertThat(calculator.calculate(FY2023, TrademarkFeeInput.of(type, classes)).total()).isEqualTo(usd(expected));
    }

    @Test
    void application_allSurcharges_fy2025() {
        // 3 classes: base 1050 + insufficient 300 + free-form 600 + 2 blocks x 3 classes x 200 = 1200 -> 3150
        FeeBreakdown quote = calculator.calculate(FY2025, new TrademarkFeeInput(APPLICATION, 3, true, true, 2, false));
        assertThat(quote.total()).isEqualTo(usd("3150.00"));
        assertThat(quote.lineItems()).extracting(LineItem::feeCode).containsExactly(FeeCodes.TM_BASE_APP,
                FeeCodes.TM_INSUFFICIENT_INFO, FeeCodes.TM_FREE_FORM, FeeCodes.TM_EXTRA_CHARS);
        assertThat(quote.lineItems().get(3).quantity()).isEqualTo(6);
        assertThat(quote.warnings()).isEmpty();
        assertThat(quote.notes()).anyMatch(n -> n.startsWith("Additional characters: 2 block(s)"));
    }

    @Test
    void application_surcharges_fy2023_warnedNotCharged() {
        FeeBreakdown quote = calculator.calculate(FY2023, new TrademarkFeeInput(APPLICATION, 2, true, true, 5, false));
        assertThat(quote.total()).isEqualTo(usd("700.00"));
        assertThat(quote.lineItems()).extracting(LineItem::feeCode).containsExactly(FeeCodes.TM_BASE_APP);
        assertThat(quote.warnings()).hasSize(3).allMatch(w -> w.contains("did not exist in fee schedule FY2023"));
    }

    @ParameterizedTest(name = "grace {0} x 2 classes -> {1}")
    @CsvSource({"SECTION_8, 850.00", "SECTION_9_RENEWAL, 850.00", "SECTION_8_AND_9, 1700.00"})
    void gracePeriodSurcharges(TrademarkFilingType type, String expected) {
        FeeBreakdown quote = calculator.calculate(FY2025, new TrademarkFeeInput(type, 2, false, false, 0, true));
        assertThat(quote.total()).isEqualTo(usd(expected));
        assertThat(quote.warnings()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TrademarkFilingType.class, names = {"APPLICATION", "STATEMENT_OF_USE", "EXTENSION_SOU", "SECTION_15"})
    void gracePeriod_notApplicable_warnedNotCharged(TrademarkFilingType type) {
        FeeBreakdown withGrace = calculator.calculate(FY2025, new TrademarkFeeInput(type, 1, false, false, 0, true));
        FeeBreakdown without = calculator.calculate(FY2025, TrademarkFeeInput.of(type, 1));
        assertThat(withGrace.total()).isEqualTo(without.total());
        assertThat(withGrace.warnings()).anyMatch(w -> w.startsWith("There is no grace-period surcharge"));
    }

    @ParameterizedTest
    @EnumSource(value = TrademarkFilingType.class, names = "APPLICATION", mode = EnumSource.Mode.EXCLUDE)
    void applicationSurcharges_onOtherFilings_ignored(TrademarkFilingType type) {
        FeeBreakdown quote = calculator.calculate(FY2025, new TrademarkFeeInput(type, 1, true, true, 3, false));
        assertThat(quote.total()).isEqualTo(calculator.calculate(FY2025, TrademarkFeeInput.of(type, 1)).total());
        assertThat(quote.warnings()).anyMatch(w -> w.startsWith("Base-application surcharges apply only"));
    }

    @ParameterizedTest(name = "classes={0}, blocks={1} -> 400 on {2}")
    @CsvSource({"0, 0, numberOfClasses", "46, 0, numberOfClasses", "1, -1, extraCharacterBlocks", "1, 101, extraCharacterBlocks"})
    void outOfRange_isRejected(int classes, int blocks, String field) {
        assertThatThrownBy(() -> calculator.calculate(FY2025, new TrademarkFeeInput(APPLICATION, classes, false, false, blocks, false)))
                .isInstanceOfSatisfying(FeeRuleViolationException.class, ex ->
                        assertThat(ex.violations()).extracting(RuleViolation::field).containsExactly(field));
    }

    @Test
    void linearInClasses_andTotalIsSumOfLines() {
        Random rnd = new Random(11);
        for (int i = 0; i < 2000; i++) {
            TrademarkFeeInput in = InputGenerators.trademark(rnd);
            for (FeeSchedule schedule : TestSchedules.all()) {
                FeeBreakdown one = calculator.calculate(schedule, new TrademarkFeeInput(in.filingType(), 1,
                        in.insufficientInformation(), in.freeFormTextIds(), in.extraCharacterBlocks(), in.inGracePeriod()));
                FeeBreakdown many = calculator.calculate(schedule, in);
                assertThat(many.total()).as("%s", in)
                        .isEqualTo(one.total().multiply(BigDecimal.valueOf(in.numberOfClasses())).setScale(2));
                assertThat(many.total()).isEqualByComparingTo(
                        many.lineItems().stream().map(LineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }
    }

    @Test
    void sectionTypes_gracePeriodFlag() {
        assertThat(SECTION_8.hasGracePeriod()).isTrue();
        assertThat(SECTION_9_RENEWAL.hasGracePeriod()).isTrue();
        assertThat(SECTION_8_AND_9.hasGracePeriod()).isTrue();
        assertThat(SECTION_15.hasGracePeriod()).isFalse();
        assertThat(STATEMENT_OF_USE.hasGracePeriod()).isFalse();
    }
}
