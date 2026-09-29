package gov.openpto.fee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.openpto.fee.domain.FeeCategory;
import gov.openpto.fee.domain.TestSchedules;
import gov.openpto.fee.dto.ScheduleDetailResponse;
import gov.openpto.fee.dto.ScheduleSummaryResponse;
import gov.openpto.fee.exception.NotFoundException;
import gov.openpto.fee.mapper.FeeScheduleMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeScheduleServiceTest {

    private static final ZoneId ET = ZoneId.of("America/New_York");

    @Mock
    private FeeScheduleCatalog catalog;

    private FeeScheduleService service;

    private FeeScheduleService serviceAt(String isoInstant) {
        return new FeeScheduleService(catalog, new FeeScheduleMapper(), Clock.fixed(Instant.parse(isoInstant), ET));
    }

    @BeforeEach
    void setUp() {
        service = serviceAt("2025-06-01T12:00:00Z");
    }

    @Test
    void list_marksCurrentSchedule() {
        when(catalog.loadAll()).thenReturn(TestSchedules.all());
        List<ScheduleSummaryResponse> list = service.list();
        assertThat(list).extracting(ScheduleSummaryResponse::code).containsExactly("FY2025", "FY2023");
        assertThat(list).extracting(ScheduleSummaryResponse::current).containsExactly(true, false);
        assertThat(list.get(1).effectiveTo()).isEqualTo(LocalDate.of(2025, 1, 18));
    }

    @Test
    void today_usesEasternTimeClock() {
        // 03:00 UTC on Jan 19 is still Jan 18 in New York -> FY2023 is current
        FeeScheduleService beforeMidnightEt = serviceAt("2025-01-19T03:00:00Z");
        assertThat(beforeMidnightEt.today()).isEqualTo(LocalDate.of(2025, 1, 18));
        when(catalog.loadAll()).thenReturn(TestSchedules.all());
        assertThat(beforeMidnightEt.get("current", null).code()).isEqualTo("FY2023");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"2025-01-18, FY2023", "2025-01-19, FY2025", "2022-12-29, FY2023"})
    void effectiveOn_delegatesToCatalog(LocalDate date, String code) {
        when(catalog.loadAll()).thenReturn(TestSchedules.all());
        assertThat(service.effectiveOn(date).code()).isEqualTo(code);
        verify(catalog, times(1)).loadAll();
    }

    @Test
    void effectiveOn_beforeAnySchedule_isNotFound() {
        when(catalog.loadAll()).thenReturn(TestSchedules.all());
        assertThatThrownBy(() -> service.effectiveOn(LocalDate.of(2020, 1, 1)))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("2020-01-01");
    }

    @Test
    void get_byCode_isCaseInsensitive_andFiltersCategory() {
        when(catalog.loadAll()).thenReturn(TestSchedules.all());
        ScheduleDetailResponse detail = service.get("fy2023", FeeCategory.TRADEMARK);
        assertThat(detail.code()).isEqualTo("FY2023");
        assertThat(detail.current()).isFalse();
        assertThat(detail.items()).hasSize(8).allMatch(i -> i.category() == FeeCategory.TRADEMARK);
    }

    @Test
    void get_current_returnsScheduleEffectiveToday() {
        when(catalog.loadAll()).thenReturn(TestSchedules.all());
        ScheduleDetailResponse detail = service.get("CURRENT", null);
        assertThat(detail.code()).isEqualTo("FY2025");
        assertThat(detail.current()).isTrue();
        assertThat(detail.items()).hasSize(TestSchedules.fy2025().items().size());
    }

    @Test
    void get_unknownCode_isNotFound() {
        when(catalog.loadAll()).thenReturn(TestSchedules.all());
        assertThatThrownBy(() -> service.get("FY1999", null)).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("FY1999");
    }
}
