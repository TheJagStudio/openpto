package gov.openpto.ingest.web;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The caller as seen by the services: JWT {@code sub} and whether it holds ROLE_ADMIN. */
public record CurrentUser(String id, boolean admin) {

    public static CurrentUser from(JwtAuthenticationToken token) {
        Jwt jwt = token.getToken();
        return new CurrentUser(jwt.getSubject(), isAdmin(token.getAuthorities()));
    }

    static boolean isAdmin(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    public boolean canAccess(String ownerId) {
        return admin || id.equals(ownerId);
    }
}