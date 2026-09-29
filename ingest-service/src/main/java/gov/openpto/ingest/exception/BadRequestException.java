package gov.openpto.ingest.exception;

import org.springframework.http.HttpStatus;

/** A request parameter was invalid; {@code field} is reported in the problem's {@code errors}. */
public class BadRequestException extends ApiException {

    private final String field;

    public BadRequestException(String field, String detail) {
        this("Bad Request", field, detail);
    }

    protected BadRequestException(String title, String field, String detail) {
        super(HttpStatus.BAD_REQUEST, title, detail);
        this.field = field;
    }

    public String field() {
        return field;
    }
}