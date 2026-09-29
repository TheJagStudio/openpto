package gov.openpto.fee.exception;

import gov.openpto.fee.domain.RuleViolation;

import java.util.List;

/** Validation failure detected outside of {@code @Valid} (e.g. the nested request of a saved quote): HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    private final transient List<RuleViolation> errors;

    public InvalidRequestException(String message, List<RuleViolation> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<RuleViolation> errors() {
        return errors;
    }
}
