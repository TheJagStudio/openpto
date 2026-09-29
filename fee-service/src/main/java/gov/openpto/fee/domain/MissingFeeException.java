package gov.openpto.fee.domain;

/** A fee code the calculator requires is absent from the schedule (a data/configuration error, HTTP 500). */
public class MissingFeeException extends IllegalStateException {

    private final String scheduleCode;
    private final String feeCode;

    public MissingFeeException(String scheduleCode, String feeCode) {
        super("Fee schedule " + scheduleCode + " has no fee " + feeCode);
        this.scheduleCode = scheduleCode;
        this.feeCode = feeCode;
    }

    public String scheduleCode() {
        return scheduleCode;
    }

    public String feeCode() {
        return feeCode;
    }
}
