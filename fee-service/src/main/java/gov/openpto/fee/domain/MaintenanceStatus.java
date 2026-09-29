package gov.openpto.fee.domain;

/** Status of one maintenance-fee window on the as-of date. */
public enum MaintenanceStatus {
    /** Before the window opens; the fee cannot be paid yet. */
    NOT_YET_OPEN,
    /** Window open, payable without surcharge (window opens .. due date, inclusive). */
    OPEN,
    /** Six-month grace period after the due date; payable with the surcharge. */
    GRACE_PERIOD,
    /** Grace period has ended and the stage is reported unpaid: the patent lapsed at the end of grace. */
    EXPIRED,
    /** Grace period has ended and the fee is assumed (or reported) paid. */
    PAID_WINDOW_PASSED
}
