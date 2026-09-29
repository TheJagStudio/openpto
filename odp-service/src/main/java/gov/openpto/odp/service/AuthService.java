package gov.openpto.odp.service;

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
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, login (with brute-force lockout) and the current user. */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AccountMapper mapper;
    private final AppProperties.Security security;
    private final Clock clock;

    /** Hash compared against when the email is unknown, so response time does not reveal accounts. */
    private volatile String timingDummyHash;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = createUser(email, request.password(), request.displayName().trim(), EnumSet.of(Role.USER));
        log.info("Registered user {}", user.getId());
        return authResponse(user);
    }

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public AuthResponse login(LoginRequest request) {
        Instant now = clock.instant();
        Optional<User> found = users.findByEmail(normalizeEmail(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash());
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (user.isLockedAt(now)) {
            throw new AccountLockedException(Duration.between(now, user.getLockedUntil()));
        }
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            users.incrementFailedLogins(user.getId(), now);
            Instant until = now.plus(security.lockoutDuration());
            if (users.lockIfThresholdReached(user.getId(), security.maxFailedLogins(), until, now) > 0) {
                log.warn("User {} locked until {} after {} failed logins", user.getId(), until, security.maxFailedLogins());
                throw new AccountLockedException(security.lockoutDuration());
            }
            throw new InvalidCredentialsException();
        }
        users.recordSuccessfulLogin(user.getId(), now);
        return authResponse(user);
    }

    public UserResponse me(UUID userId) {
        return users.findWithRolesById(userId)
                .map(mapper::toResponse)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }

    /** Creates a user; also used by the admin bootstrap. */
    @Transactional
    public User createUser(String email, String rawPassword, String displayName, Set<Role> roles) {
        Instant now = clock.instant();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(normalizeEmail(email));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setRoles(EnumSet.copyOf(roles));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("An account with this email already exists");
        }
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse authResponse(User user) {
        TokenService.IssuedToken token = tokens.issue(user);
        return AuthResponse.bearer(token.value(), token.expiresInSeconds(), mapper.toResponse(user));
    }

    private String dummyHash() {
        String hash = timingDummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode(UUID.randomUUID().toString());
            timingDummyHash = hash;
        }
        return hash;
    }
}
