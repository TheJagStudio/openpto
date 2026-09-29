package gov.openpto.odp.repository.spec;

import jakarta.persistence.criteria.CriteriaQuery;

import java.util.Locale;

/** Shared helpers for Criteria and SQL filter builders. */
public final class SpecSupport {

    public static final char ESCAPE = '\\';

    private SpecSupport() {
    }

    /** {@code %text%}, lower-cased, with LIKE wildcards in user input escaped. */
    public static String containsPattern(String text) {
        return "%" + escape(text.trim().toLowerCase(Locale.ROOT)) + "%";
    }

    public static String prefixPattern(String text) {
        return escape(text) + "%";
    }

    public static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** Spring Data reuses the specification for the count query; ordering there is pointless. */
    static boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> type = query.getResultType();
        return Long.class.equals(type) || long.class.equals(type);
    }
}
