package gov.openpto.fee.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Maintenance-fee request. {@code paidStages == null} means payment history is unknown: windows whose grace
 * period has ended are assumed paid. When supplied, a passed window missing from the set is EXPIRED.
 */
public record MaintenanceInput(EntitySize entitySize, LocalDate grantDate, LocalDate asOfDate,
                               Set<MaintenanceStage> paidStages) {

    public MaintenanceInput {
        Objects.requireNonNull(entitySize, "entitySize");
        Objects.requireNonNull(grantDate, "grantDate");
        Objects.requireNonNull(asOfDate, "asOfDate");
        paidStages = paidStages == null ? null : Set.copyOf(paidStages);
    }

    public MaintenanceInput(EntitySize entitySize, LocalDate grantDate, LocalDate asOfDate) {
        this(entitySize, grantDate, asOfDate, null);
    }
}
