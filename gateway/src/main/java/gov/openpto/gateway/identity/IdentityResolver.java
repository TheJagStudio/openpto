package gov.openpto.gateway.identity;

import gov.openpto.gateway.config.GatewayProperties;
import gov.openpto.gateway.config.GatewayProperties.TierLimit;
import gov.openpto.gateway.web.GatewayHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the caller of a rate-limited route, in contract order:
 * <ol>
 *   <li>{@code X-API-Key} header or {@code api_key} query param → verified via odp-service (401 if invalid)</li>
 *   <li>{@code Authorization: Bearer <jwt>} → validated locally against the JWKS (401 if invalid/expired);
 *       tier WEB, or ADMIN when the {@code roles} claim contains ADMIN</li>
 *   <li>otherwise anonymous, keyed by client IP</li>
 * </ol>
 * The query string is parsed by hand: {@code getParameter()} would consume form bodies we must proxy.
 */
@Component
public class IdentityResolver {

    private static final String BEARER_PREFIX = "bearer ";

    private final ApiKeyVerifier apiKeyVerifier;
    private final JwtDecoder jwtDecoder;
    private final ClientIpResolver clientIpResolver;
    private final GatewayProperties.RateLimit limits;
    private final String identityProviderName;

    public IdentityResolver(ApiKeyVerifier apiKeyVerifier, JwtDecoder jwtDecoder, ClientIpResolver clientIpResolver,
                            GatewayProperties properties) {
        this.apiKeyVerifier = apiKeyVerifier;
        this.jwtDecoder = jwtDecoder;
        this.clientIpResolver = clientIpResolver;
        this.limits = properties.rateLimit();
        GatewayProperties.Service odp = properties.services().get(GatewayProperties.ODP);
        this.identityProviderName = odp == null ? "odp-service" : odp.displayName();
    }

    public Identity resolve(HttpServletRequest request) {
        String apiKey = apiKey(request);
        if (apiKey != null) {
            return fromApiKey(apiKey);
        }
        String token = bearerToken(request);
        if (token != null) {
            return fromJwt(token);
        }
        TierLimit anon = limits.limitFor(Tier.ANONYMOUS);
        return new Identity(Identity.Type.IP, clientIpResolver.resolve(request), Tier.ANONYMOUS,
                anon.perMinute(), anon.perDay());
    }

    private Identity fromApiKey(String apiKey) {
        ApiKeyVerification v = apiKeyVerifier.verify(apiKey);
        if (!v.valid()) {
            throw InvalidCredentialsException.apiKey();
        }
        Tier tier = Tier.fromApiKeyTier(v.tier());
        TierLimit defaults = limits.limitFor(tier);
        long perMinute = v.perMinute() != null && v.perMinute() > 0 ? v.perMinute() : defaults.perMinute();
        long perDay = v.perDay() != null && v.perDay() > 0 ? v.perDay() : defaults.perDay();
        String id = v.keyId() != null ? v.keyId() : String.valueOf(v.userId());
        return new Identity(Identity.Type.KEY, id, tier, perMinute, perDay);
    }

    private Identity fromJwt(String token) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (BadJwtException e) {
            throw InvalidCredentialsException.bearer(summarize(e));
        } catch (JwtException e) {
            throw new UpstreamUnavailableException(identityProviderName, "Unable to verify the bearer token", e);
        }
        Tier tier = hasRole(jwt, "ADMIN") ? Tier.ADMIN : Tier.WEB;
        TierLimit limit = limits.limitFor(tier);
        return new Identity(Identity.Type.USER, jwt.getSubject(), tier, limit.perMinute(), limit.perDay());
    }

    static boolean hasRole(Jwt jwt, String role) {
        Object roles = jwt.getClaims().get("roles");
        if (roles instanceof Collection<?> c) {
            return c.stream().anyMatch(r -> role.equalsIgnoreCase(String.valueOf(r)));
        }
        if (roles instanceof String s) {
            return List.of(s.split("[,\\s]+")).stream().anyMatch(role::equalsIgnoreCase);
        }
        return false;
    }

    static String apiKey(HttpServletRequest request) {
        String header = request.getHeader(GatewayHeaders.API_KEY);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            return null;
        }
        String raw = UriComponentsBuilder.newInstance().query(query).build()
                .getQueryParams().getFirst(GatewayHeaders.API_KEY_QUERY_PARAM);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return URLDecoder.decode(raw, StandardCharsets.UTF_8).trim();
    }

    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.length() <= BEARER_PREFIX.length()
                || !header.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static String summarize(JwtException e) {
        String msg = e.getMessage() == null ? "invalid token" : e.getMessage();
        if (msg.toLowerCase(Locale.ROOT).contains("expired")) {
            return "token expired";
        }
        return msg.length() > 160 ? msg.substring(0, 160) : msg;
    }
}
