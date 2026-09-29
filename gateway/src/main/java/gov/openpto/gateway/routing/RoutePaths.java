package gov.openpto.gateway.routing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * Single source of truth for path patterns, shared by the router ({@link RoutesConfig}) and the servlet
 * filters, so "which requests are limited" can never drift from "which requests are proxied". Paths are
 * matched with the same {@link PathPattern} algorithm on the raw request path that the router uses.
 */
public final class RoutePaths {

    public static final List<String> ODP_DATA = List.of(
            "/api/v1/patents/**", "/api/v1/trademarks/**", "/api/v1/stats/**");
    public static final List<String> ODP_DATASETS = List.of("/api/v1/datasets/**");
    public static final List<String> ODP_ACCOUNTS = List.of(
            "/api/v1/auth/**", "/api/v1/account/**", "/api/v1/admin/**", "/.well-known/**");
    public static final List<String> FEES = List.of("/api/v1/fees/**");
    public static final List<String> INGEST = List.of("/api/v1/ingest/**");
    public static final List<String> INGEST_PUBLIC = List.of("/api/v1/ingest/samples", "/api/v1/ingest/samples/**");
    public static final List<String> INTERNAL = List.of("/internal/**");
    /** POST-only, limited per IP. */
    public static final List<String> AUTH_LIMITED = List.of("/api/v1/auth/login", "/api/v1/auth/register");

    private static final List<PathPattern> TIERED_PATTERNS = parse(
            concat(ODP_DATA, ODP_DATASETS, FEES));
    private static final List<PathPattern> AUTH_LIMITED_PATTERNS = parse(AUTH_LIMITED);

    private RoutePaths() {
    }

    /** Data + fee routes: API key / JWT / anonymous tiering applies. */
    public static boolean isTiered(HttpServletRequest request) {
        return matches(TIERED_PATTERNS, request);
    }

    /** Login/register: strict per-IP limiter applies. */
    public static boolean isAuthLimited(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && matches(AUTH_LIMITED_PATTERNS, request);
    }

    public static PathContainer pathWithinApplication(HttpServletRequest request) {
        return RequestPath.parse(request.getRequestURI(), request.getContextPath()).pathWithinApplication();
    }

    private static boolean matches(List<PathPattern> patterns, HttpServletRequest request) {
        PathContainer path = pathWithinApplication(request);
        for (PathPattern p : patterns) {
            if (p.matches(path)) {
                return true;
            }
        }
        return false;
    }

    @SafeVarargs
    static List<String> concat(List<String>... lists) {
        return java.util.Arrays.stream(lists).flatMap(List::stream).toList();
    }

    private static List<PathPattern> parse(List<String> patterns) {
        return patterns.stream().map(PathPatternParser.defaultInstance::parse).toList();
    }
}
