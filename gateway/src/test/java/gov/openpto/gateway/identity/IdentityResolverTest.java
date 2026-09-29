package gov.openpto.gateway.identity;

import gov.openpto.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityResolverTest {

    static final String KEY = "opto_" + "x".repeat(40);

    @Mock
    ApiKeyVerifier apiKeyVerifier;
    @Mock
    JwtDecoder jwtDecoder;

    IdentityResolver resolver;
    MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        GatewayProperties properties = new GatewayProperties(
                Map.of("odp", new GatewayProperties.Service("odp-service", URI.create("http://odp"), null, null)),
                null, "t", List.of("10.0.0.0/8"), null, null, null, null, null, null, null, null);
        resolver = new IdentityResolver(apiKeyVerifier, jwtDecoder, new ClientIpResolver(properties), properties);
        request = new MockHttpServletRequest("GET", "/api/v1/patents");
        request.setRemoteAddr("203.0.113.9");
    }

    @Test
    void resolve_apiKeyHeader_usesVerifiedTierAndLimits() {
        request.addHeader("X-API-Key", KEY);
        when(apiKeyVerifier.verify(KEY)).thenReturn(new ApiKeyVerification(true, "k-1", "u-1", "FREE", 99L, 999L));

        Identity identity = resolver.resolve(request);

        assertThat(identity.type()).isEqualTo(Identity.Type.KEY);
        assertThat(identity.bucketKey()).isEqualTo("key:k-1");
        assertThat(identity.tier()).isEqualTo(Tier.FREE);
        assertThat(identity.perMinute()).isEqualTo(99);
        assertThat(identity.perDay()).isEqualTo(999);
    }

    @Test
    void resolve_apiKeyQueryParam_isAccepted_andTierDefaultsApplyWhenLimitsMissing() {
        request.setQueryString("q=x&api_key=" + KEY);
        when(apiKeyVerifier.verify(KEY)).thenReturn(new ApiKeyVerification(true, "k-2", "u", "unknown-tier", null, 0L));

        Identity identity = resolver.resolve(request);

        assertThat(identity.bucketKey()).isEqualTo("key:k-2");
        assertThat(identity.tier()).isEqualTo(Tier.FREE);
        assertThat(identity.perMinute()).isEqualTo(120);
        assertThat(identity.perDay()).isEqualTo(20_000);
    }

    @Test
    void resolve_invalidApiKey_throws401_evenWithValidJwt() {
        request.addHeader("X-API-Key", KEY);
        request.addHeader("Authorization", "Bearer abc");
        when(apiKeyVerifier.verify(KEY)).thenReturn(ApiKeyVerification.invalid());

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(e -> assertThat(((InvalidCredentialsException) e).isBearer()).isFalse());
        verify(jwtDecoder, never()).decode(anyString());
    }

    @Test
    void resolve_jwt_userIsWebTier_adminIsAdminTier() {
        request.addHeader("Authorization", "Bearer user-token");
        when(jwtDecoder.decode("user-token")).thenReturn(jwt("sub-1", List.of("USER")));
        Identity user = resolver.resolve(request);
        assertThat(user.bucketKey()).isEqualTo("user:sub-1");
        assertThat(user.tier()).isEqualTo(Tier.WEB);
        assertThat(user.perMinute()).isEqualTo(300);

        MockHttpServletRequest adminRequest = new MockHttpServletRequest("GET", "/api/v1/fees/schedules");
        adminRequest.addHeader("Authorization", "bearer admin-token");
        when(jwtDecoder.decode("admin-token")).thenReturn(jwt("sub-2", List.of("USER", "ADMIN")));
        Identity admin = resolver.resolve(adminRequest);
        assertThat(admin.tier()).isEqualTo(Tier.ADMIN);
        assertThat(admin.perDay()).isEqualTo(1_000_000);
    }

    @Test
    void resolve_rolesAsString_isSupported() {
        request.addHeader("Authorization", "Bearer t");
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("s").claim("roles", "USER ADMIN")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode("t")).thenReturn(jwt);
        assertThat(resolver.resolve(request).tier()).isEqualTo(Tier.ADMIN);
    }

    @Test
    void resolve_expiredJwt_throwsBearer401() {
        request.addHeader("Authorization", "Bearer expired");
        when(jwtDecoder.decode("expired")).thenThrow(new BadJwtException("Jwt expired at 2025-01-01T00:00:00Z"));

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("token expired")
                .satisfies(e -> assertThat(((InvalidCredentialsException) e).isBearer()).isTrue());
    }

    @Test
    void resolve_jwksUnavailable_isUpstreamFailure() {
        request.addHeader("Authorization", "Bearer t");
        when(jwtDecoder.decode("t")).thenThrow(new JwtException("Couldn't retrieve remote JWK set"));

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(UpstreamUnavailableException.class)
                .satisfies(e -> assertThat(((UpstreamUnavailableException) e).serviceName()).isEqualTo("odp-service"));
    }

    @Test
    void resolve_noCredentials_isAnonymousByRemoteIp() {
        request.addHeader("X-Forwarded-For", "1.2.3.4"); // peer is not a trusted proxy → ignored
        Identity identity = resolver.resolve(request);
        assertThat(identity.bucketKey()).isEqualTo("ip:203.0.113.9");
        assertThat(identity.tier()).isEqualTo(Tier.ANONYMOUS);
        assertThat(identity.perMinute()).isEqualTo(30);
        assertThat(identity.perDay()).isEqualTo(1_000);
    }

    @Test
    void resolve_anonymousBehindTrustedProxy_usesForwardedFor() {
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.9.9.9");
        assertThat(resolver.resolve(request).bucketKey()).isEqualTo("ip:198.51.100.7");
    }

    @Test
    void bearerToken_ignoresOtherSchemesAndBlanks() {
        request.addHeader("Authorization", "Basic abc");
        assertThat(IdentityResolver.bearerToken(request)).isNull();
        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.addHeader("Authorization", "Bearer    ");
        assertThat(IdentityResolver.bearerToken(blank)).isNull();
        assertThat(IdentityResolver.apiKey(new MockHttpServletRequest())).isNull();
    }

    private static Jwt jwt(String subject, List<String> roles) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject(subject).claim("roles", roles)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
