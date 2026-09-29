package gov.openpto.fee.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers: every amount leaving the core has scale 2, rounded HALF_UP. */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    public static BigDecimal of(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        return amount.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(long wholeDollars) {
        return BigDecimal.valueOf(wholeDollars).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String amount) {
        return of(new BigDecimal(amount));
    }

    public static BigDecimal times(BigDecimal unitAmount, int quantity) {
        return of(unitAmount.multiply(BigDecimal.valueOf(quantity)));
    }
}
