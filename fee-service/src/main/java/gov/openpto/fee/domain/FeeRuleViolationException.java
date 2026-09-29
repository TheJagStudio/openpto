package gov.openpto.fee.domain;

import java.util.List;
import java.util.stream.Collectors;

/** The request is well-formed but breaks a fee rule (HTTP 400 with field errors). */
public class FeeRuleViolationException extends RuntimeException {

    private final transient List<RuleViolation> violations;

    public FeeRuleViolationException(List<RuleViolation> violations) {
        super(violations.stream().map(RuleViolation::message).collect(Collectors.joining("; ")));
        this.violations = List.copyOf(violations);
    }

    public List<RuleViolation> violations() {
        return violations;
    }
}
