package gov.openpto.fee.service;

import gov.openpto.fee.domain.FeeSchedule;
import gov.openpto.fee.domain.MaintenanceCalculator;
import gov.openpto.fee.domain.PatentFilingCalculator;
import gov.openpto.fee.domain.TrademarkFeeCalculator;
import gov.openpto.fee.dto.FeeQuoteResponse;
import gov.openpto.fee.dto.MaintenanceRequest;
import gov.openpto.fee.dto.MaintenanceResponse;
import gov.openpto.fee.dto.PatentFilingRequest;
import gov.openpto.fee.dto.TrademarkFeeRequest;
import gov.openpto.fee.mapper.FeeQuoteMapper;

import java.time.LocalDate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thin application service: resolves the schedule effective on the request date and delegates to the pure
 * calculators. All fee rules live in {@code gov.openpto.fee.domain}.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FeeCalculationService {

    private final FeeScheduleService schedules;
    private final PatentFilingCalculator patentFilingCalculator;
    private final MaintenanceCalculator maintenanceCalculator;
    private final TrademarkFeeCalculator trademarkFeeCalculator;
    private final FeeQuoteMapper mapper;

    public FeeQuoteResponse patentFiling(PatentFilingRequest request) {
        FeeSchedule schedule = schedules.effectiveOn(dateOrToday(request.filingDate()));
        return mapper.toResponse(patentFilingCalculator.calculate(schedule, mapper.toInput(request)));
    }

    public MaintenanceResponse maintenance(MaintenanceRequest request) {
        LocalDate asOf = dateOrToday(request.asOfDate());
        FeeSchedule schedule = schedules.effectiveOn(asOf);
        return mapper.toResponse(maintenanceCalculator.calculate(schedule, mapper.toInput(request, asOf)));
    }

    public FeeQuoteResponse trademark(TrademarkFeeRequest request) {
        FeeSchedule schedule = schedules.effectiveOn(dateOrToday(request.filingDate()));
        return mapper.toResponse(trademarkFeeCalculator.calculate(schedule, mapper.toInput(request)));
    }

    public LocalDate dateOrToday(LocalDate date) {
        return date != null ? date : schedules.today();
    }
}
