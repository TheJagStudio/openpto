package gov.openpto.fee.exception;

/** One entry of the ProblemDetail {@code errors} array. */
public record FieldErrorResponse(String field, String message) {
}
