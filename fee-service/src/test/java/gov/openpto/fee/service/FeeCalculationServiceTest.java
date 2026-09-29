package gov.openpto.fee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.openpto.fee.domain.ApplicationType;
import gov.openpto.fee.domain.EntitySize;
import gov.openpto.fee.domain.FeeBreakdown;
import gov.openpto.fee.domain.MaintenanceCalculator;
import gov.openpto.fee.domain.MaintenanceInput;
import gov.openpto.fee.domain.MaintenanceStage;
import gov.openpto.fee.domain.PatentFilingCalculator;
import gov.openpto.fee.domain.PatentFilingInput;
import gov.openpto.fee.domain.TestSchedules;
import gov.openpto.fee.domain.TrademarkFeeCalculator;
import gov.openpto.fee.domain.TrademarkFeeInput;
import gov.openpto.fee.domain.TrademarkFilingType;
import gov.openpto.fee.dto.FeeQuoteResponse;
import gov.openpto.fee.dto.MaintenanceRequest;
import gov.openpto.fee.dto.MaintenanceResponse;
import gov.openpto.fee.dto.PatentFilingRequest;
import gov.openpto.fee.dto.TrademarkFeeRequest;
import gov.openpto.fee.mapper.FeeQuoteMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeCalculationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 1);

    @Mock
    private FeeScheduleService schedules;
    @Spy
    private PatentFilingCalculator patentCalculator = new PatentFilingCalculator();
    @Spy
    private MaintenanceCalculator maintenanceCalculator = new MaintenanceCalculator();
    @Mock
    private TrademarkFeeCalculator trademarkCalculator;

    private FeeCalculationService service() {
        return new FeeCalculationService(schedules, patentCalculator, maintenanceCalculator, trademarkCalculator,
                new FeeQuoteMapper());
    }

    private static PatentFilingRequest filing(LocalDate date) {
        return new PatentFilingRequest(ApplicationType.UTILITY, EntitySize.SMALL, 25, 5, null, null, null, null,
                null, null, null, date);
    }

    @Test
    void patentFiling_withoutDate_usesTodaysSchedule_andDelegatesToCalculator() {
        when(schedules.today()).thenReturn(TODAY);
        when(schedules.effectiveOn(TODAY)).thenReturn(TestSchedules.fy2025());

        FeeQuoteResponse quote = service().patentFiling(filing(null));

        assertThat(quote.total()).isEqualTo(new BigDecimal("1680.00"));
        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.scheduleCode()).isEqualTo("FY2025");
        ArgumentCaptor<PatentFilingInput> input = ArgumentCaptor.forClass(PatentFilingInput.class);
        verify(patentCalculator).calculate(eq(TestSchedules.fy2025()), input.capture());
        assertThat(input.getValue().filedElectronically()).as("default true").isTrue();
        assertThat(input.getValue().specificationSheets()).isZero();
    }

    @Test
    void patentFiling_withDate_selectsScheduleForThatDate() {
        LocalDate d = LocalDate.of(2025, 1, 18);
        when(schedules.effectiveOn(d)).thenReturn(TestSchedules.fy2023());
        FeeQuoteResponse quote = service().patentFiling(filing(d));
        assertThat(quote.scheduleCode()).isEqualTo("FY2023");
        assertThat(quote.total()).isEqualTo(new BigDecimal("1312.00")); // 728 + 5 x 40 + 2 x 192
        verify(schedules, never()).today();
    }

    @Test
    void trademark_delegatesNormalizedInput() {
        when(schedules.today()).thenReturn(TODAY);
        when(schedules.effectiveOn(TODAY)).thenReturn(TestSchedules.fy2025());
        FeeBreakdown stub = FeeBreakdown.builder(TestSchedules.fy2025(), null).charge("TM_SEC15", 2).build();
        when(trademarkCalculator.calculate(any(), any())).thenReturn(stub);

        FeeQuoteResponse quote = service().trademark(
                new TrademarkFeeRequest(TrademarkFilingType.SECTION_15, 2, null, null, null, null, null));

        assertThat(quote.total()).isEqualTo(new BigDecimal("500.00"));
        assertThat(quote.lineItems()).singleElement().satisfies(l -> {
            assertThat(l.quantity()).isEqualTo(2);
            assertThat(l.unitAmount()).isEqualTo(new BigDecimal("250.00"));
        });
        verify(trademarkCalculator).calculate(TestSchedules.fy2025(),
                new TrademarkFeeInput(TrademarkFilingType.SECTION_15, 2, false, false, 0, false));
    }

    @Test
    void maintenance_mapsWindowsAndPaidStages() {
        LocalDate asOf = LocalDate.of(2029, 1, 1);
        when(schedules.effectiveOn(asOf)).thenReturn(TestSchedules.fy2025());

        MaintenanceResponse r = service().maintenance(
                new MaintenanceRequest(EntitySize.SMALL, LocalDate.of(2021, 6, 15), asOf, List.of("3.5")));

        assertThat(r.windows()).extracting(w -> w.stage()).containsExactly("3.5", "7.5", "11.5");
        assertThat(r.windows().get(1).totalIfPaidOnAsOfDate()).isEqualTo(new BigDecimal("1832.00"));
        assertThat(r.payableNow().total()).isEqualTo(new BigDecimal("1832.00"));
        assertThat(r.scheduleCode()).isEqualTo("FY2025");
        ArgumentCaptor<MaintenanceInput> input = ArgumentCaptor.forClass(MaintenanceInput.class);
        verify(maintenanceCalculator).calculate(eq(TestSchedules.fy2025()), input.capture());
        assertThat(input.getValue().paidStages()).containsExactly(MaintenanceStage.STAGE_3_5);
    }

    @Test
    void maintenance_withoutAsOf_usesToday() {
        when(schedules.today()).thenReturn(TODAY);
        when(schedules.effectiveOn(TODAY)).thenReturn(TestSchedules.fy2025());
        MaintenanceResponse r = service().maintenance(new MaintenanceRequest(EntitySize.LARGE, LocalDate.of(2021, 6, 15), null, null));
        assertThat(r.asOfDate()).isEqualTo(TODAY);
        // grant 2021-06-15: the 3.5-year grace period runs until 2025-06-15
        assertThat(r.windows().getFirst().status().name()).isEqualTo("GRACE_PERIOD");
    }
}
