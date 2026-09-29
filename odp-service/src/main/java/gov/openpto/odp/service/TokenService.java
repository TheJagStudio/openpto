package gov.openpto.odp.service;

import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.config.JwtConfig;
import gov.openpto.odp.model.Role;
import gov.openpto.odp.model.User;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues RS256 access tokens: iss, sub (user id), email, name, roles, iat, exp, jti. */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties.Jwt props;
    private final Clock clock;

    public record IssuedToken(String value, long expiresInSeconds) {

        @Override
        public String toString() {
            return "IssuedToken[expiresIn=" + expiresInSeconds + "]";
        }
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.ttl()))
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim(JwtConfig.ROLES_CLAIM, user.getRoles().stream().sorted().map(Role::name).toList())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, props.ttl().toSeconds());
    }
}
