package gov.openpto.odp.controller;

import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

final class Principals {

    private Principals() {
    }

    /** The user id is the JWT {@code sub}. */
    static UUID userId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new BadCredentialsException("Missing subject");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Malformed subject");
        }
    }
}
