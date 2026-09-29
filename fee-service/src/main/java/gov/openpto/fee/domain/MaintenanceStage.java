package gov.openpto.fee.domain;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * The three utility-patent maintenance stages. All offsets are whole months from the grant date so that
 * month-end and leap-day grants clamp the same way everywhere (2020-02-29 + 36 months = 2023-02-28,
 * + 42 months = 2023-08-29, + 48 months = 2024-02-29).
 */
public enum MaintenanceStage {
    STAGE_3_5("3.5", 36, 42, FeeCodes.MAINT_3_5),
    STAGE_7_5("7.5", 84, 90, FeeCodes.MAINT_7_5),
    STAGE_11_5("11.5", 132, 138, FeeCodes.MAINT_11_5);

    /** Grace period after the due date, in months. */
    public static final int GRACE_MONTHS = 6;

    private final String label;
    private final int opensAfterMonths;
    private final int dueAfterMonths;
    private final String feeCode;

    MaintenanceStage(String label, int opensAfterMonths, int dueAfterMonths, String feeCode) {
        this.label = label;
        this.opensAfterMonths = opensAfterMonths;
        this.dueAfterMonths = dueAfterMonths;
        this.feeCode = feeCode;
    }

    public String label() {
        return label;
    }

    public String feeCode() {
        return feeCode;
    }

    public LocalDate windowOpens(LocalDate grantDate) {
        return grantDate.plusMonths(opensAfterMonths);
    }

    public LocalDate dueDate(LocalDate grantDate) {
        return grantDate.plusMonths(dueAfterMonths);
    }

    public LocalDate graceEnds(LocalDate grantDate) {
        return grantDate.plusMonths(dueAfterMonths + GRACE_MONTHS);
    }

    public static MaintenanceStage fromLabel(String label) {
        return Arrays.stream(values())
                .filter(s -> s.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown maintenance stage: " + label));
    }
}
