package gov.openpto.fee.exception;

/** Requested resource (schedule, quote) does not exist: HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
