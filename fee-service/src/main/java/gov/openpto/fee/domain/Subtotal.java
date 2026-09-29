package gov.openpto.fee.domain;

import java.math.BigDecimal;

public record Subtotal(String group, BigDecimal amount) {
}
