package gov.openpto.odp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.dto.AuthResponse;
import gov.openpto.odp.dto.LoginRequest;
import gov.openpto.odp.dto.RegisterRequest;
import gov.openpto.odp.dto.UserResponse;
import gov.openpto.odp.exception.AccountLockedException;
import gov.openpto.odp.exception.ConflictException;
import gov.openpto.odp.exception.InvalidCredentialsException;
import gov.openpto.odp.exception.NotFoundException;
import gov.openpto.odp.mapper.AccountMapper;
import gov.openpto.odp.model.Role;
import gov.openpto.odp.model.User;
import gov.openpto.odp.repository.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Mock
    UserRepository users;

    @Mock
    PasswordEncoder encoder;

    @Mock
    TokenService tokens;

    @Mock
    AccountMapper mapper;

    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, tokens, mapper,
                new AppProperties.Security("t", 5, Duration.ofMinutes(15)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User user(String hash) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("ada@example.com");
        u.setPasswordHash(hash);
        u.setDisplayName("Ada");
        u.setRoles(EnumSet.of(Role.USER));
        return u;
    }

    @Test
    void register_newEmail_normalizesHashesAndIssuesToken() {
        given(users.existsByEmail("ada@example.com")).willReturn(false);
        given(encoder.encode("secret-pass-1")).willReturn("{bcrypt}hash");
        given(users.saveAndFlush(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(tokens.issue(any())).willReturn(new TokenService.IssuedToken("jwt", 28800));
        given(mapper.toResponse(any(User.class)))
                .willReturn(new UserResponse(UUID.randomUUID(), "ada@example.com", "Ada", List.of("USER"), NOW));

        AuthResponse response = service.register(new RegisterRequest("  Ada@Example.com ", "secret-pass-1", " Ada "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Ada");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(saved.getValue().getRoles()).containsExactly(Role.USER);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(28800);
    }

    @Test
    void register_existingEmail_throwsConflict() {
        given(users.existsByEmail("ada@example.com")).willReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("ada@example.com", "secret-pass-1", "Ada")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void createUser_concurrentDuplicate_throwsConflict() {
        given(encoder.encode(anyString())).willReturn("h");
        given(users.saveAndFlush(any(User.class))).willThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> service.createUser("a@b.co", "pw", "A", EnumSet.of(Role.USER)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void login_correctPassword_recordsSuccessAndIssuesToken() {
        User u = user("h");
        given(users.findByEmail("ada@example.com")).willReturn(Optional.of(u));
        given(encoder.matches("pw-123456789", "h")).willReturn(true);
        given(tokens.issue(u)).willReturn(new TokenService.IssuedToken("jwt", 100));

        AuthResponse response = service.login(new LoginRequest("ADA@example.com", "pw-123456789"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        verify(users).recordSuccessfulLogin(u.getId(), NOW);
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentialsAfterDummyHashCheck() {
        given(users.findByEmail("ghost@example.com")).willReturn(Optional.empty());
        given(encoder.encode(anyString())).willReturn("dummy");

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "pw")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(encoder).matches("pw", "dummy");
    }

    @Test
    void login_wrongPassword_incrementsFailuresAndThrowsInvalid() {
        User u = user("h");
        given(users.findByEmail("ada@example.com")).willReturn(Optional.of(u));
        given(encoder.matches("bad", "h")).willReturn(false);
        given(users.lockIfThresholdReached(eq(u.getId()), eq(5), any(), eq(NOW))).willReturn(0);

        assertThatThrownBy(() -> service.login(new LoginRequest("ada@example.com", "bad")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(users).incrementFailedLogins(u.getId(), NOW);
        verify(users, never()).recordSuccessfulLogin(any(), any());
    }

    @Test
    void login_fifthFailure_locksAccountFor15Minutes() {
        User u = user("h");
        given(users.findByEmail("ada@example.com")).willReturn(Optional.of(u));
        given(encoder.matches("bad", "h")).willReturn(false);
        given(users.lockIfThresholdReached(u.getId(), 5, NOW.plus(Duration.ofMinutes(15)), NOW)).willReturn(1);

        assertThatThrownBy(() -> service.login(new LoginRequest("ada@example.com", "bad")))
                .isInstanceOf(AccountLockedException.class)
                .satisfies(e -> assertThat(((AccountLockedException) e).getRetryAfter()).isEqualTo(Duration.ofMinutes(15)));
    }

    @Test
    void login_lockedAccount_rejectsWithoutCheckingPassword() {
        User u = user("h");
        u.setLockedUntil(NOW.plusSeconds(120));
        given(users.findByEmail("ada@example.com")).willReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new LoginRequest("ada@example.com", "right")))
                .isInstanceOf(AccountLockedException.class)
                .satisfies(e -> assertThat(((AccountLockedException) e).getRetryAfter()).isEqualTo(Duration.ofSeconds(120)));
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_expiredLock_allowsLogin() {
        User u = user("h");
        u.setLockedUntil(NOW.minusSeconds(1));
        given(users.findByEmail("ada@example.com")).willReturn(Optional.of(u));
        given(encoder.matches("right", "h")).willReturn(true);
        given(tokens.issue(u)).willReturn(new TokenService.IssuedToken("jwt", 1));

        assertThat(service.login(new LoginRequest("ada@example.com", "right")).accessToken()).isEqualTo("jwt");
    }

    @Test
    void login_disabledUser_treatedAsBadCredentials() {
        User u = user("h");
        u.setEnabled(false);
        given(users.findByEmail("ada@example.com")).willReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new LoginRequest("ada@example.com", "right")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void me_knownUser_mapsResponse() {
        User u = user("h");
        UserResponse expected = new UserResponse(u.getId(), u.getEmail(), "Ada", List.of("USER"), NOW);
        given(users.findWithRolesById(u.getId())).willReturn(Optional.of(u));
        given(mapper.toResponse(u)).willReturn(expected);

        assertThat(service.me(u.getId())).isEqualTo(expected);
    }

    @Test
    void me_unknownUser_throwsNotFound() {
        UUID id = UUID.randomUUID();
        given(users.findWithRolesById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(id)).isInstanceOf(NotFoundException.class);
    }
}
