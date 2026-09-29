package gov.openpto.fee.mapper;

import gov.openpto.fee.domain.ContinuedExamination;
import gov.openpto.fee.domain.FeeBreakdown;
import gov.openpto.fee.domain.MaintenanceInput;
import gov.openpto.fee.domain.MaintenanceResult;
import gov.openpto.fee.domain.MaintenanceStage;
import gov.openpto.fee.domain.PatentFilingInput;
import gov.openpto.fee.domain.TrademarkFeeInput;
import gov.openpto.fee.dto.FeeQuoteResponse;
import gov.openpto.fee.dto.LineItemResponse;
import gov.openpto.fee.dto.MaintenanceRequest;
import gov.openpto.fee.dto.MaintenanceResponse;
import gov.openpto.fee.dto.MaintenanceWindowResponse;
import gov.openpto.fee.dto.PatentFilingRequest;
import gov.openpto.fee.dto.SubtotalResponse;
import gov.openpto.fee.dto.TrademarkFeeRequest;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/** Request DTO -> domain input (applying defaults) and domain result -> response DTO. */
@Component
public class FeeQuoteMapper {

    public PatentFilingInput toInput(PatentFilingRequest r) {
        return new PatentFilingInput(
                r.applicationType(),
                r.entitySize(),
                orZero(r.totalClaims()),
                orZero(r.independentClaims()),
                Boolean.TRUE.equals(r.multipleDependentClaims()),
                orZero(r.specificationSheets()),
                !Boolean.FALSE.equals(r.filedElectronically()),
                Boolean.TRUE.equals(r.lateFilingSurcharge()),
                orZero(r.extensionMonths()),
                r.continuedExamination() == null ? ContinuedExamination.NONE : r.continuedExamination(),
                Boolean.TRUE.equals(r.prioritizedExamination()));
    }

    public TrademarkFeeInput toInput(TrademarkFeeRequest r) {
        return new TrademarkFeeInput(
                r.filingType(),
                orZero(r.numberOfClasses()),
                Boolean.TRUE.equals(r.insufficientInformation()),
                Boolean.TRUE.equals(r.freeFormTextIds()),
                orZero(r.extraCharacterBlocks()),
                Boolean.TRUE.equals(r.inGracePeriod()));
    }

    public MaintenanceInput toInput(MaintenanceRequest r, LocalDate asOfDate) {
        Set<MaintenanceStage> paid = r.paidStages() == null ? null
                : r.paidStages().stream().map(MaintenanceStage::fromLabel).collect(Collectors.toSet());
        return new MaintenanceInput(r.entitySize(), r.grantDate(), asOfDate, paid);
    }

    public FeeQuoteResponse toResponse(FeeBreakdown b) {
        return new FeeQuoteResponse(
                b.scheduleCode(),
                b.scheduleName(),
                b.entitySize(),
                b.lineItems().stream()
                        .map(l -> new LineItemResponse(l.feeCode(), l.description(), l.quantity(), l.unitAmount(), l.amount()))
                        .toList(),
                b.subtotals().stream().map(s -> new SubtotalResponse(s.group(), s.amount())).toList(),
                b.total(),
                FeeQuoteResponse.CURRENCY,
                b.notes(),
                b.warnings());
    }

    public MaintenanceResponse toResponse(MaintenanceResult m) {
        return new MaintenanceResponse(
                m.windows().stream()
                        .map(w -> new MaintenanceWindowResponse(w.stage().label(), w.windowOpens(), w.dueDate(),
                                w.graceEnds(), w.status(), w.fee(), w.surcharge(), w.totalIfPaidOnAsOfDate()))
                        .toList(),
                m.patentExpired(),
                m.scheduleCode(),
                m.scheduleName(),
                m.entitySize(),
                m.grantDate(),
                m.asOfDate(),
                toResponse(m.payableNow()));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
