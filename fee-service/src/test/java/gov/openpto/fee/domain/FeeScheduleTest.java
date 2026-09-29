package gov.openpto.fee.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FeeScheduleTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "2022-12-28, NONE",
            "2022-12-29, FY2023",
            "2024-06-30, FY2023",
            "2025-01-18, FY2023",
            "2025-01-19, FY2025",
            "2025-01-20, FY2025",
            "2099-12-31, FY2025",
    })
    void effectiveOn_selectsScheduleByDate(LocalDate date, String expected) {
        String actual = FeeSchedule.effectiveOn(TestSchedules.all(), date).map(FeeSchedule::code).orElse("NONE");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void effectiveOn_overlappingRanges_latestStartWins() {
        FeeSchedule older = new FeeSchedule(1L, "A", "a", LocalDate.of(2020, 1, 1), null, List.of());
        FeeSchedule newer = new FeeSchedule(2L, "B", "b", LocalDate.of(2024, 1, 1), null, List.of());
        assertThat(FeeSchedule.effectiveOn(List.of(older, newer), LocalDate.of(2024, 5, 1))).get()
                .extracting(FeeSchedule::code).isEqualTo("B");
    }

    @Test
    void isEffectiveOn_isInclusiveAtBothEnds() {
        FeeSchedule fy2023 = TestSchedules.fy2023();
        assertThat(fy2023.isEffectiveOn(TestSchedules.FY2023_START)).isTrue();
        assertThat(fy2023.isEffectiveOn(TestSchedules.FY2023_END)).isTrue();
        assertThat(fy2023.isEffectiveOn(TestSchedules.FY2023_END.plusDays(1))).isFalse();
        assertThat(fy2023.isEffectiveOn(TestSchedules.FY2023_START.minusDays(1))).isFalse();
    }

    @Test
    void constructor_rejectsInvertedRange() {
        assertThatThrownBy(() -> new FeeSchedule(1L, "X", "x", LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lookups() {
        FeeSchedule s = TestSchedules.fy2025();
        assertThat(s.has(FeeCodes.TRACK_ONE)).isTrue();
        assertThat(s.find("NOPE")).isEmpty();
        assertThat(s.itemsIn(FeeCategory.TRADEMARK)).allMatch(i -> i.feeCode().startsWith("TM_")).hasSize(11);
        assertThatThrownBy(() -> s.require("NOPE")).isInstanceOf(MissingFeeException.class).hasMessageContaining("NOPE");
        assertThat(TestSchedules.fy2023().has(FeeCodes.TM_FREE_FORM)).isFalse();
    }

    @Test
    void scheduleAmounts_smallAbout40_microAbout20PercentOfLarge() {
        for (FeeSchedule s : TestSchedules.all()) {
            for (FeeRate r : s.itemsIn(FeeCategory.PATENT)) {
                if (r.feeCode().equals(FeeCodes.NON_ELECTRONIC)) {
                    assertThat(r.microEntity()).isEqualTo(r.smallEntity()); // micro pays the small rate
                    continue;
                }
                assertThat(r.smallEntity()).as(r.feeCode()).isEqualByComparingTo(r.largeEntity().multiply(new BigDecimal("0.4")));
                assertThat(r.microEntity()).as(r.feeCode()).isEqualByComparingTo(r.largeEntity().multiply(new BigDecimal("0.2")));
            }
        }
    }

    @Test
    void feeRate_normalizesScale_andPricesByEntity() {
        FeeRate rate = new FeeRate("X", "x", FeeCategory.PATENT, "G", new BigDecimal("10"), new BigDecimal("4.005"),
                new BigDecimal("2.004"), FeeUnit.EACH);
        assertThat(rate.largeEntity()).isEqualTo(new BigDecimal("10.00"));
        assertThat(rate.amountFor(EntitySize.SMALL)).isEqualTo(new BigDecimal("4.01")); // HALF_UP
        assertThat(rate.amountFor(EntitySize.MICRO)).isEqualTo(new BigDecimal("2.00"));
    }

    @Test
    void money_helpers() {
        assertThat(Money.of("1.005")).isEqualTo(new BigDecimal("1.01"));
        assertThat(Money.of("1.004")).isEqualTo(new BigDecimal("1.00"));
        assertThat(Money.of(5)).isEqualTo(new BigDecimal("5.00"));
        assertThat(Money.times(new BigDecimal("0.35"), 3)).isEqualTo(new BigDecimal("1.05"));
        assertThat(Money.ZERO).isEqualTo(new BigDecimal("0.00"));
        assertThatThrownBy(() -> Money.of((BigDecimal) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void breakdownBuilder_guards() {
        FeeBreakdown.Builder b = FeeBreakdown.builder(TestSchedules.fy2025(), EntitySize.LARGE);
        assertThatThrownBy(() -> b.charge(FeeCodes.UTIL_FILING, -1)).isInstanceOf(IllegalArgumentException.class);
        FeeBreakdown empty = b.note("n").note("n").warn("w").warn("w").chargeIfDefined(FeeCodes.UTIL_FILING, 0, "x").build();
        assertThat(empty.total()).isEqualTo(Money.ZERO);
        assertThat(empty.notes()).containsExactly("n");
        assertThat(empty.warnings()).containsExactly("w");
        assertThat(empty.amountOf(FeeCodes.UTIL_FILING)).isEqualTo(Money.ZERO);
    }

    @Test
    void applicationTypeCapabilities() {
        assertThat(ApplicationType.UTILITY.chargesExcessClaims()).isTrue();
        assertThat(ApplicationType.DESIGN.chargesExcessClaims()).isFalse();
        assertThat(ApplicationType.PLANT.allowsContinuedExamination()).isTrue();
        assertThat(ApplicationType.PROVISIONAL.allowsContinuedExamination()).isFalse();
        assertThat(ApplicationType.REISSUE.allowsPrioritizedExamination()).isTrue();
        assertThat(ApplicationType.PLANT.allowsPrioritizedExamination()).isFalse();
    }
}
