package gov.openpto.odp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.model.Role;
import gov.openpto.odp.model.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {

    @Test
    void issue_signsRs256TokenWithContractClaims() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("test-kid").algorithm(JWSAlgorithm.RS256).generate();
        Instant now = Instant.now();
        TokenService service = new TokenService(
                new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))),
                new AppProperties.Jwt("openpto", Duration.ofHours(8), "unused"),
                Clock.fixed(now, java.time.ZoneOffset.UTC));
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ada@example.com");
        user.setDisplayName("Ada");
        user.setRoles(EnumSet.of(Role.ADMIN, Role.USER));

        TokenService.IssuedToken token = service.issue(user);

        Jwt jwt = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(token.value());
        assertThat(token.expiresInSeconds()).isEqualTo(28_800);
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", "test-kid");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("openpto");
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ada@example.com");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Ada");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER", "ADMIN");
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofHours(8)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(jwt.getId()).isNotBlank();
    }
}
