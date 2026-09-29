package gov.openpto.fee.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One maintenance stage evaluated on the as-of date. {@code surcharge} is the grace-period surcharge for
 * the stage; {@code totalIfPaidOnAsOfDate} is null when the fee cannot be paid on that date.
 */
public record MaintenanceWindow(
        MaintenanceStage stage,
        LocalDate windowOpens,
        LocalDate dueDate,
        LocalDate graceEnds,
        MaintenanceStatus status,
        BigDecimal fee,
        BigDecimal surcharge,
        BigDecimal totalIfPaidOnAsOfDate) {

    public boolean payable() {
        return status == MaintenanceStatus.OPEN || status == MaintenanceStatus.GRACE_PERIOD;
    }
}
