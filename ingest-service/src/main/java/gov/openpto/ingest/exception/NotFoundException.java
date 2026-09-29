package gov.openpto.ingest.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "Not Found", detail);
    }
}