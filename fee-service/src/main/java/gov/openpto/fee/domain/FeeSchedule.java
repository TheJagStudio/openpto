package gov.openpto.fee.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable, versioned fee schedule. Together with the request it is the only input the calculators
 * need, which keeps the calculation core free of Spring and JPA.
 */
public record FeeSchedule(
        Long id,
        String code,
        String name,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<FeeRate> items) {

    public FeeSchedule {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo before effectiveFrom for schedule " + code);
        }
        items = List.copyOf(items);
    }

    /** Inclusive on both ends; an open-ended schedule ({@code effectiveTo == null}) never ends. */
    public boolean isEffectiveOn(LocalDate date) {
        return !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }

    public Optional<FeeRate> find(String feeCode) {
        return items.stream().filter(i -> i.feeCode().equals(feeCode)).findFirst();
    }

    public boolean has(String feeCode) {
        return find(feeCode).isPresent();
    }

    public FeeRate require(String feeCode) {
        return find(feeCode).orElseThrow(() -> new MissingFeeException(code, feeCode));
    }

    public List<FeeRate> itemsIn(FeeCategory category) {
        return items.stream().filter(i -> i.category() == category).toList();
    }

    /**
     * Picks the schedule effective on {@code date}. Should ranges ever overlap, the most recently
     * started schedule wins.
     */
    public static Optional<FeeSchedule> effectiveOn(Collection<FeeSchedule> schedules, LocalDate date) {
        return schedules.stream()
                .filter(s -> s.isEffectiveOn(date))
                .max(Comparator.comparing(FeeSchedule::effectiveFrom));
    }
}
