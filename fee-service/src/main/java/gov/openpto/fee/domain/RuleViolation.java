package gov.openpto.fee.domain;

/** A business-rule violation tied to a request field. */
public record RuleViolation(String field, String message) {
}
