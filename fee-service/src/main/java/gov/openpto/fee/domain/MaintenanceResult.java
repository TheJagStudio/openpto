package gov.openpto.fee.domain;

import java.time.LocalDate;
import java.util.List;

/** Maintenance windows for a patent plus the itemized fees payable on the as-of date. */
public record MaintenanceResult(
        String scheduleCode,
        String scheduleName,
        EntitySize entitySize,
        LocalDate grantDate,
        LocalDate asOfDate,
        List<MaintenanceWindow> windows,
        boolean patentExpired,
        FeeBreakdown payableNow) {

    public MaintenanceResult {
        windows = List.copyOf(windows);
    }
}
