package gov.openpto.fee.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a fee calculation: itemized lines, per-group subtotals, total, notes and warnings.
 * Subtotals and total are always derived from the lines, so {@code total == sum(lineItems.amount)}.
 */
public record FeeBreakdown(
        String scheduleCode,
        String scheduleName,
        EntitySize entitySize,
        List<LineItem> lineItems,
        List<Subtotal> subtotals,
        BigDecimal total,
        List<String> notes,
        List<String> warnings) {

    public FeeBreakdown {
        lineItems = List.copyOf(lineItems);
        subtotals = List.copyOf(subtotals);
        notes = List.copyOf(notes);
        warnings = List.copyOf(warnings);
    }

    /** Amount charged for a fee code (zero if not charged). */
    public BigDecimal amountOf(String feeCode) {
        return Money.of(lineItems.stream()
                .filter(l -> l.feeCode().equals(feeCode))
                .map(LineItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public static Builder builder(FeeSchedule schedule, EntitySize entitySize) {
        return new Builder(schedule, entitySize);
    }

    /** Accumulates line items, notes and warnings. */
    public static final class Builder {
        private final FeeSchedule schedule;
        private final EntitySize entitySize;
        private final List<LineItem> lines = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private Builder(FeeSchedule schedule, EntitySize entitySize) {
            this.schedule = schedule;
            this.entitySize = entitySize;
        }

        /** Adds {@code quantity} units of a fee the schedule must define; zero quantity adds nothing. */
        public Builder charge(String feeCode, int quantity) {
            if (quantity < 0) {
                throw new IllegalArgumentException("quantity must be >= 0");
            }
            if (quantity > 0) {
                lines.add(LineItem.of(schedule.require(feeCode), pricingEntity(), quantity));
            }
            return this;
        }

        /**
         * Charges the fee only if the schedule defines it; otherwise records {@code warningIfMissing} and
         * charges nothing. Used for fees that did not exist in older schedules.
         */
        public Builder chargeIfDefined(String feeCode, int quantity, String warningIfMissing) {
            if (quantity <= 0) {
                return this;
            }
            if (schedule.has(feeCode)) {
                return charge(feeCode, quantity);
            }
            return warn(warningIfMissing);
        }

        public Builder note(String note) {
            if (!notes.contains(note)) {
                notes.add(note);
            }
            return this;
        }

        public Builder warn(String warning) {
            if (!warnings.contains(warning)) {
                warnings.add(warning);
            }
            return this;
        }

        private EntitySize pricingEntity() {
            // Trademark fees are not entity-discounted: the quote has no entity size and uses the single amount.
            return entitySize == null ? EntitySize.LARGE : entitySize;
        }

        public FeeBreakdown build() {
            Map<String, BigDecimal> byGroup = new LinkedHashMap<>();
            BigDecimal total = Money.ZERO;
            for (LineItem line : lines) {
                byGroup.merge(line.group(), line.amount(), BigDecimal::add);
                total = total.add(line.amount());
            }
            List<Subtotal> subtotals = byGroup.entrySet().stream()
                    .map(e -> new Subtotal(e.getKey(), Money.of(e.getValue())))
                    .toList();
            return new FeeBreakdown(schedule.code(), schedule.name(), entitySize, lines, subtotals,
                    Money.of(total), notes, warnings);
        }
    }
}
