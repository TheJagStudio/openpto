package gov.openpto.odp.repository.spec;

import gov.openpto.odp.exception.BadRequestException;

import java.util.Locale;
import java.util.Set;

/**
 * A validated {@code sort=field,asc|desc} parameter. {@code relevance} is only meaningful with a
 * full-text query; without one it falls back to the default field.
 */
public record SearchSort(String field, boolean ascending) {

    public static final String RELEVANCE = "relevance";

    public boolean isRelevance() {
        return RELEVANCE.equals(field);
    }

    /**
     * @param raw          the raw parameter (nullable)
     * @param allowed      allowed field names (must include {@code relevance})
     * @param hasQuery     whether a full-text query is present
     * @param defaultField default when absent (or when relevance is requested without a query)
     */
    public static SearchSort parse(String raw, Set<String> allowed, boolean hasQuery, String defaultField) {
        if (raw == null || raw.isBlank()) {
            return hasQuery ? new SearchSort(RELEVANCE, false) : new SearchSort(defaultField, false);
        }
        String[] parts = raw.split(",", 2);
        String field = parts[0].trim();
        if (!allowed.contains(field)) {
            throw new BadRequestException("sort field must be one of " + String.join(", ", allowed.stream().sorted().toList()));
        }
        boolean ascending = parts.length > 1 && parts[1].trim().toLowerCase(Locale.ROOT).equals("asc");
        if (RELEVANCE.equals(field) && !hasQuery) {
            return new SearchSort(defaultField, false);
        }
        return new SearchSort(field, ascending);
    }
}
