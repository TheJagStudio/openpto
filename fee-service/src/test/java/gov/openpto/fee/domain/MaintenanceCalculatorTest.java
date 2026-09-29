package gov.openpto.fee.domain;

import static gov.openpto.fee.domain.MaintenanceStatus.EXPIRED;
import static gov.openpto.fee.domain.MaintenanceStatus.GRACE_PERIOD;
import static gov.openpto.fee.domain.MaintenanceStatus.NOT_YET_OPEN;
import static gov.openpto.fee.domain.MaintenanceStatus.OPEN;
import static gov.openpto.fee.domain.MaintenanceStatus.PAID_WINDOW_PASSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MaintenanceCalculatorTest {

    private final MaintenanceCalculator calculator = new MaintenanceCalculator();
    private static final FeeSchedule FY2025 = TestSchedules.fy2025();
    private static final LocalDate GRANT = LocalDate.of(2021, 6, 15);

    private MaintenanceResult calc(EntitySize entity, LocalDate grant, LocalDate asOf) {
        return calculator.calculate(FY2025, new MaintenanceInput(entity, grant, asOf));
    }

    private static MaintenanceWindow window(MaintenanceResult r, MaintenanceStage stage) {
        return r.windows().stream().filter(w -> w.stage() == stage).findFirst().orElseThrow();
    }

    private static BigDecimal usd(String v) {
        return new BigDecimal(v).setScale(2);
    }

    @Nested
    class WindowDates {

        @ParameterizedTest(name = "grant {0} stage {1}: opens {2}, due {3}, grace ends {4}")
        @CsvSource({
                // ordinary date
                "2021-06-15, 3.5,  2024-06-15, 2024-12-15, 2025-06-15",
                "2021-06-15, 7.5,  2028-06-15, 2028-12-15, 2029-06-15",
                "2021-06-15, 11.5, 2032-06-15, 2032-12-15, 2033-06-15",
                // leap-day grant: clamps to Feb 28 in common years, keeps the 29th otherwise
                "2020-02-29, 3.5,  2023-02-28, 2023-08-29, 2024-02-29",
                "2020-02-29, 7.5,  2027-02-28, 2027-08-29, 2028-02-29",
                "2020-02-29, 11.5, 2031-02-28, 2031-08-29, 2032-02-29",
                // month-end grant: due date clamps to end of February
                "2021-08-31, 3.5,  2024-08-31, 2025-02-28, 2025-08-31",
                "2016-08-31, 3.5,  2019-08-31, 2020-02-29, 2020-08-31",
                "2021-12-31, 7.5,  2028-12-31, 2029-06-30, 2029-12-31",
        })
        void windowDates(LocalDate grant, String stageLabel, LocalDate opens, LocalDate due, LocalDate graceEnds) {
            MaintenanceStage stage = MaintenanceStage.fromLabel(stageLabel);
            MaintenanceWindow w = window(calc(EntitySize.LARGE, grant, grant), stage);
            assertThat(w.windowOpens()).isEqualTo(opens);
            assertThat(w.dueDate()).isEqualTo(due);
            assertThat(w.graceEnds()).isEqualTo(graceEnds);
        }

        @Test
        void unknownStageLabel_isRejected() {
            assertThatThrownBy(() -> MaintenanceStage.fromLabel("4.5")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class StatusBoundaries {

        @ParameterizedTest(name = "stage {0}, asOf {1} -> {2}")
        @CsvSource({
                "3.5,  2024-06-14, NOT_YET_OPEN",
                "3.5,  2024-06-15, OPEN",
                "3.5,  2024-06-16, OPEN",
                "3.5,  2024-12-14, OPEN",
                "3.5,  2024-12-15, OPEN",
                "3.5,  2024-12-16, GRACE_PERIOD",
                "3.5,  2025-06-14, GRACE_PERIOD",
                "3.5,  2025-06-15, GRACE_PERIOD",
                "3.5,  2025-06-16, PAID_WINDOW_PASSED",
                "7.5,  2028-06-14, NOT_YET_OPEN",
                "7.5,  2028-06-15, OPEN",
                "7.5,  2028-12-15, OPEN",
                "7.5,  2028-12-16, GRACE_PERIOD",
                "7.5,  2029-06-15, GRACE_PERIOD",
                "7.5,  2029-06-16, PAID_WINDOW_PASSED",
                "11.5, 2032-06-14, NOT_YET_OPEN",
                "11.5, 2032-06-15, OPEN",
                "11.5, 2032-12-15, OPEN",
                "11.5, 2032-12-16, GRACE_PERIOD",
                "11.5, 2033-06-15, GRACE_PERIOD",
                "11.5, 2033-06-16, PAID_WINDOW_PASSED",
        })
        void statusOnBoundaryDays(String stageLabel, LocalDate asOf, MaintenanceStatus expected) {
            assertThat(window(calc(EntitySize.SMALL, GRANT, asOf), MaintenanceStage.fromLabel(stageLabel)).status())
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "leap grant, asOf {0} -> 3.5 {1}")
        @CsvSource({
                "2023-02-27, NOT_YET_OPEN", "2023-02-28, OPEN", "2023-08-29, OPEN", "2023-08-30, GRACE_PERIOD",
                "2024-02-29, GRACE_PERIOD", "2024-03-01, PAID_WINDOW_PASSED",
        })
        void leapDayGrant_boundaries(LocalDate asOf, MaintenanceStatus expected) {
            assertThat(window(calc(EntitySize.MICRO, LocalDate.of(2020, 2, 29), asOf), MaintenanceStage.STAGE_3_5).status())
                    .isEqualTo(expected);
        }

        @Test
        void asOfOnGrantDate_allWindowsNotYetOpen() {
            MaintenanceResult r = calc(EntitySize.LARGE, GRANT, GRANT);
            assertThat(r.windows()).extracting(MaintenanceWindow::status).containsOnly(NOT_YET_OPEN);
            assertThat(r.payableNow().lineItems()).isEmpty();
            assertThat(r.payableNow().notes()).anyMatch(n -> n.startsWith("No maintenance fee is payable"));
            assertThat(r.patentExpired()).isFalse();
        }

        @Test
        void asOfBeforeGrant_isRejected() {
            assertThatThrownBy(() -> calc(EntitySize.LARGE, GRANT, GRANT.minusDays(1)))
                    .isInstanceOfSatisfying(FeeRuleViolationException.class, ex ->
                            assertThat(ex.violations()).extracting(RuleViolation::field).containsExactly("asOfDate"));
        }
    }

    @Nested
    class Amounts {

        @ParameterizedTest(name = "stage {0} {1}: fee {2}, surcharge {3}")
        @CsvSource({
                "3.5,  LARGE, 2150.00, 540.00", "3.5,  SMALL, 860.00,  216.00", "3.5,  MICRO, 430.00,  108.00",
                "7.5,  LARGE, 4040.00, 540.00", "7.5,  SMALL, 1616.00, 216.00", "7.5,  MICRO, 808.00,  108.00",
                "11.5, LARGE, 8280.00, 540.00", "11.5, SMALL, 3312.00, 216.00", "11.5, MICRO, 1656.00, 108.00",
        })
        void feesAndSurcharges(String stageLabel, EntitySize entity, String fee, String surcharge) {
            MaintenanceStage stage = MaintenanceStage.fromLabel(stageLabel);
            LocalDate openDay = stage.windowOpens(GRANT);
            LocalDate graceDay = stage.dueDate(GRANT).plusDays(1);

            MaintenanceWindow open = window(calc(entity, GRANT, openDay), stage);
            assertThat(open.fee()).isEqualTo(usd(fee));
            assertThat(open.surcharge()).isEqualTo(usd(surcharge));
            assertThat(open.totalIfPaidOnAsOfDate()).isEqualTo(usd(fee));

            MaintenanceResult inGrace = calc(entity, GRANT, graceDay);
            assertThat(window(inGrace, stage).totalIfPaidOnAsOfDate()).isEqualTo(usd(fee).add(usd(surcharge)));
            assertThat(inGrace.payableNow().total()).isEqualTo(usd(fee).add(usd(surcharge)));
            assertThat(inGrace.payableNow().lineItems()).extracting(LineItem::feeCode)
                    .containsExactly(stage.feeCode(), FeeCodes.MAINT_SURCHARGE);
            assertThat(inGrace.payableNow().warnings()).anyMatch(w -> w.contains("grace period"));
        }

        @Test
        void notPayableStatuses_haveNoTotal() {
            MaintenanceResult r = calc(EntitySize.LARGE, GRANT, LocalDate.of(2026, 1, 1));
            assertThat(window(r, MaintenanceStage.STAGE_3_5).status()).isEqualTo(PAID_WINDOW_PASSED);
            assertThat(window(r, MaintenanceStage.STAGE_3_5).totalIfPaidOnAsOfDate()).isNull();
            assertThat(window(r, MaintenanceStage.STAGE_7_5).totalIfPaidOnAsOfDate()).isNull();
            assertThat(window(r, MaintenanceStage.STAGE_7_5).payable()).isFalse();
        }

        @Test
        void fy2023Schedule_usesItsOwnAmounts() {
            MaintenanceResult r = calculator.calculate(TestSchedules.fy2023(),
                    new MaintenanceInput(EntitySize.SMALL, LocalDate.of(2020, 1, 10), LocalDate.of(2023, 8, 1)));
            MaintenanceWindow w = window(r, MaintenanceStage.STAGE_3_5);
            assertThat(w.status()).isEqualTo(GRACE_PERIOD);
            assertThat(w.totalIfPaidOnAsOfDate()).isEqualTo(usd("1000.00"));
            assertThat(r.scheduleCode()).isEqualTo("FY2023");
        }
    }

    @Nested
    class PaymentHistory {

        @Test
        void unpaidPassedStage_isExpired_andLaterStagesToo() {
            MaintenanceResult r = calculator.calculate(FY2025, new MaintenanceInput(EntitySize.LARGE, GRANT,
                    LocalDate.of(2029, 1, 1), Set.of()));
            assertThat(r.windows()).extracting(MaintenanceWindow::status).containsExactly(EXPIRED, EXPIRED, EXPIRED);
            assertThat(r.patentExpired()).isTrue();
            assertThat(r.payableNow().lineItems()).isEmpty();
            assertThat(r.payableNow().warnings()).anyMatch(w -> w.startsWith("The 3.5-year maintenance fee was not paid by 2025-06-15"));
        }

        @Test
        void paidFirstStage_secondInGrace() {
            MaintenanceResult r = calculator.calculate(FY2025, new MaintenanceInput(EntitySize.SMALL, GRANT,
                    LocalDate.of(2029, 1, 1), EnumSet.of(MaintenanceStage.STAGE_3_5)));
            assertThat(r.windows()).extracting(MaintenanceWindow::status)
                    .containsExactly(PAID_WINDOW_PASSED, GRACE_PERIOD, NOT_YET_OPEN);
            assertThat(r.patentExpired()).isFalse();
            assertThat(r.payableNow().total()).isEqualTo(usd("1832.00"));
        }

        @Test
        void unknownHistory_assumesPaid_andSaysSo() {
            MaintenanceResult r = calc(EntitySize.LARGE, GRANT, LocalDate.of(2040, 1, 1));
            assertThat(r.windows()).extracting(MaintenanceWindow::status).containsOnly(PAID_WINDOW_PASSED);
            assertThat(r.patentExpired()).isFalse();
            assertThat(r.payableNow().notes()).anyMatch(n -> n.startsWith("Payment history not supplied"));
        }
    }

    @Test
    void invariants_overRandomGrantsAndDates() {
        Random rnd = new Random(2025);
        for (int i = 0; i < 3000; i++) {
            LocalDate grant = InputGenerators.grantDate(rnd);
            LocalDate asOf = grant.plusDays(rnd.nextInt(15 * 366));
            EntitySize entity = InputGenerators.pick(rnd, EntitySize.values());
            MaintenanceResult r = calc(entity, grant, asOf);
            assertThat(r.windows()).hasSize(3);
            assertThat(r.windows().stream().filter(MaintenanceWindow::payable).count()).isLessThanOrEqualTo(1);
            BigDecimal payable = r.windows().stream().map(MaintenanceWindow::totalIfPaidOnAsOfDate)
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(r.payableNow().total()).isEqualByComparingTo(payable);
            for (MaintenanceWindow w : r.windows()) {
                assertThat(w.windowOpens()).isBefore(w.dueDate());
                assertThat(w.dueDate()).isBefore(w.graceEnds());
                assertThat(w.status()).isNotEqualTo(EXPIRED);
            }
            // micro <= small <= large for the amount payable on the same day
            BigDecimal large = calc(EntitySize.LARGE, grant, asOf).payableNow().total();
            BigDecimal micro = calc(EntitySize.MICRO, grant, asOf).payableNow().total();
            assertThat(micro).isLessThanOrEqualTo(large);
        }
    }

    @Test
    void input_copiesPaidStages_andRequiresFields() {
        Set<MaintenanceStage> paid = EnumSet.of(MaintenanceStage.STAGE_3_5);
        MaintenanceInput in = new MaintenanceInput(EntitySize.LARGE, GRANT, GRANT, paid);
        paid.add(MaintenanceStage.STAGE_7_5);
        assertThat(in.paidStages()).containsExactly(MaintenanceStage.STAGE_3_5);
        assertThatThrownBy(() -> new MaintenanceInput(null, GRANT, GRANT)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void openWindowPayableNow_hasSingleLine() {
        MaintenanceResult r = calc(EntitySize.MICRO, GRANT, LocalDate.of(2024, 7, 1));
        assertThat(window(r, MaintenanceStage.STAGE_3_5).status()).isEqualTo(OPEN);
        assertThat(r.payableNow().lineItems()).singleElement()
                .satisfies(l -> assertThat(l.amount()).isEqualTo(usd("430.00")));
        assertThat(r.payableNow().notes()).contains(FeeNotes.ENTITY_DISCOUNT);
    }
}
