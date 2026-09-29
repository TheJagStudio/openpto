package gov.openpto.fee.domain;

import java.math.BigDecimal;

/** One charged fee: {@code amount = unitAmount x quantity}, scale 2. */
public record LineItem(String feeCode, String description, String group, int quantity,
                       BigDecimal unitAmount, BigDecimal amount) {

    public static LineItem of(FeeRate rate, EntitySize entitySize, int quantity) {
        BigDecimal unit = rate.amountFor(entitySize);
        return new LineItem(rate.feeCode(), rate.description(), rate.group(), quantity, unit,
                Money.times(unit, quantity));
    }
}
