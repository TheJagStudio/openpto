package gov.openpto.fee.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility-patent maintenance-fee windows. For each stage (3.5 / 7.5 / 11.5 years):
 * <pre>
 *   [grant+3y .. grant+3.5y]         OPEN          fee
 *   (grant+3.5y .. grant+4y]         GRACE_PERIOD  fee + surcharge
 *   after grant+4y                   PAID_WINDOW_PASSED (assumed/reported paid) or EXPIRED (reported unpaid)
 * </pre>
 * (7 / 7.5 / 8 and 11 / 11.5 / 12 years for the later stages.) Once a stage is EXPIRED the patent has lapsed,
 * so every later stage is EXPIRED too.
 */
public final class MaintenanceCalculator {

    public MaintenanceResult calculate(FeeSchedule schedule, MaintenanceInput in) {
        if (in.asOfDate().isBefore(in.grantDate())) {
            throw new FeeRuleViolationException(List.of(new RuleViolation("asOfDate", "must not be before grantDate")));
        }
        EntitySize entity = in.entitySize();
        BigDecimal surcharge = schedule.require(FeeCodes.MAINT_SURCHARGE).amountFor(entity);
        FeeBreakdown.Builder payable = FeeBreakdown.builder(schedule, entity)
                .note(FeeNotes.ILLUSTRATIVE)
                .note("Maintenance fees apply to utility (and utility reissue) patents only.");
        if (entity != EntitySize.LARGE) {
            payable.note(FeeNotes.ENTITY_DISCOUNT);
        }
        if (in.paidStages() == null) {
            payable.note("Payment history not supplied: windows whose grace period has ended are assumed paid.");
        }
        payable.note("Patent term (20 years from the earliest effective filing date) is not evaluated.");

        List<MaintenanceWindow> windows = new ArrayList<>();
        boolean lapsed = false;
        for (MaintenanceStage stage : MaintenanceStage.values()) {
            BigDecimal fee = schedule.require(stage.feeCode()).amountFor(entity);
            MaintenanceStatus status = lapsed ? MaintenanceStatus.EXPIRED : statusOn(stage, in);
            if (status == MaintenanceStatus.EXPIRED && !lapsed) {
                lapsed = true;
                payable.warn("The " + stage.label() + "-year maintenance fee was not paid by "
                        + stage.graceEnds(in.grantDate()) + "; the patent expired at the end of the grace period.");
            }
            BigDecimal totalIfPaid = switch (status) {
                case OPEN -> fee;
                case GRACE_PERIOD -> Money.of(fee.add(surcharge));
                default -> null;
            };
            if (status == MaintenanceStatus.OPEN) {
                payable.charge(stage.feeCode(), 1);
            } else if (status == MaintenanceStatus.GRACE_PERIOD) {
                payable.charge(stage.feeCode(), 1).charge(FeeCodes.MAINT_SURCHARGE, 1)
                        .warn("The " + stage.label() + "-year fee is in its grace period; the surcharge applies.");
            }
            windows.add(new MaintenanceWindow(stage, stage.windowOpens(in.grantDate()), stage.dueDate(in.grantDate()),
                    stage.graceEnds(in.grantDate()), status, fee, surcharge, totalIfPaid));
        }
        FeeBreakdown breakdown = payable.build();
        if (breakdown.lineItems().isEmpty() && !lapsed) {
            payable.note("No maintenance fee is payable on " + in.asOfDate() + ".");
            breakdown = payable.build();
        }
        return new MaintenanceResult(schedule.code(), schedule.name(), entity, in.grantDate(), in.asOfDate(),
                windows, lapsed, breakdown);
    }

    /** Date-based status of one stage (inclusive boundaries: window-open day and due day are OPEN). */
    static MaintenanceStatus statusOn(MaintenanceStage stage, MaintenanceInput in) {
        LocalDate asOf = in.asOfDate();
        LocalDate grant = in.grantDate();
        if (asOf.isBefore(stage.windowOpens(grant))) {
            return MaintenanceStatus.NOT_YET_OPEN;
        }
        if (!asOf.isAfter(stage.dueDate(grant))) {
            return MaintenanceStatus.OPEN;
        }
        if (!asOf.isAfter(stage.graceEnds(grant))) {
            return MaintenanceStatus.GRACE_PERIOD;
        }
        if (in.paidStages() == null || in.paidStages().contains(stage)) {
            return MaintenanceStatus.PAID_WINDOW_PASSED;
        }
        return MaintenanceStatus.EXPIRED;
    }
}
