package gov.openpto.fee.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** One fee item of a schedule with its explicit large/small/micro amounts. */
public record FeeRate(
        String feeCode,
        String description,
        FeeCategory category,
        String group,
        BigDecimal largeEntity,
        BigDecimal smallEntity,
        BigDecimal microEntity,
        FeeUnit unit) {

    public FeeRate {
        Objects.requireNonNull(feeCode, "feeCode");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(unit, "unit");
        largeEntity = Money.of(largeEntity);
        smallEntity = Money.of(smallEntity);
        microEntity = Money.of(microEntity);
    }

    public BigDecimal amountFor(EntitySize entitySize) {
        return switch (entitySize) {
            case LARGE -> largeEntity;
            case SMALL -> smallEntity;
            case MICRO -> microEntity;
        };
    }
}
