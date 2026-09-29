package gov.openpto.odp.service;

import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.exception.ConflictException;
import gov.openpto.odp.model.Role;
import gov.openpto.odp.repository.UserRepository;

import java.util.EnumSet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Creates the admin account ({@code app.admin.*}) on startup if it does not exist yet. */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AppProperties.Admin admin;
    private final UserRepository users;
    private final AuthService authService;

    @Override
    public void run(ApplicationArguments args) {
        if (!admin.enabled()) {
            return;
        }
        String email = AuthService.normalizeEmail(admin.email());
        if (users.existsByEmail(email)) {
            return;
        }
        try {
            authService.createUser(email, admin.password(), admin.displayName(), EnumSet.of(Role.USER, Role.ADMIN));
            log.info("Created admin account {}", email);
        } catch (ConflictException raced) {
            log.debug("Admin account {} was created concurrently", email);
        }
    }
}
