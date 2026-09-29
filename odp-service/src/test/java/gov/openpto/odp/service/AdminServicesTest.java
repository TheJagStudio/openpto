package gov.openpto.odp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.dto.AdminUserResponse;
import gov.openpto.odp.dto.PageParams;
import gov.openpto.odp.dto.PageResponse;
import gov.openpto.odp.exception.ConflictException;
import gov.openpto.odp.mapper.AccountMapperImpl;
import gov.openpto.odp.model.Role;
import gov.openpto.odp.model.User;
import gov.openpto.odp.repository.ApiKeyCounts;
import gov.openpto.odp.repository.ApiKeyRepository;
import gov.openpto.odp.repository.UserRepository;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminServicesTest {

    @Mock
    UserRepository users;

    @Mock
    ApiKeyRepository keys;

    @Mock
    AuthService authService;

    private static User user(String email, Role... roles) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setDisplayName(email);
        u.setRoles(EnumSet.copyOf(List.of(roles)));
        return u;
    }

    @Test
    void list_joinsKeyCountsForThePage() {
        User a = user("a@x.io", Role.USER, Role.ADMIN);
        User b = user("b@x.io", Role.USER);
        Page<User> page = new PageImpl<>(List.of(a, b), PageRequest.of(0, 20), 2);
        given(users.findAll(any(Pageable.class))).willReturn(page);
        given(keys.countsForUsers(List.of(a.getId(), b.getId()))).willReturn(List.of(new ApiKeyCounts(a.getId(), 3, 2)));

        PageResponse<AdminUserResponse> result =
                new AdminUserService(users, keys, new AccountMapperImpl()).list(null, PageParams.defaults());

        assertThat(result.content()).extracting(AdminUserResponse::email).containsExactly("a@x.io", "b@x.io");
        assertThat(result.content().get(0).roles()).containsExactly("USER", "ADMIN");
        assertThat(result.content().get(0).activeKeys()).isEqualTo(2);
        assertThat(result.content().get(0).totalKeys()).isEqualTo(3);
        assertThat(result.content().get(1).totalKeys()).isZero();
    }

    @Test
    void list_withEmailFilter_andEmptyPage_skipsCountQuery() {
        given(users.findByEmailContainingIgnoreCase(eq("zzz"), any(Pageable.class))).willReturn(Page.empty());

        PageResponse<AdminUserResponse> result =
                new AdminUserService(users, keys, new AccountMapperImpl()).list(" zzz ", PageParams.defaults());

        assertThat(result.content()).isEmpty();
        verifyNoInteractions(keys);
    }

    @Test
    void bootstrap_createsAdminOnce() {
        AppProperties.Admin props = new AppProperties.Admin(true, "Admin@OpenPTO.local", "pw", "Admin");
        given(users.existsByEmail("admin@openpto.local")).willReturn(false, true);
        AdminBootstrap bootstrap = new AdminBootstrap(props, users, authService);

        bootstrap.run(null);
        bootstrap.run(null);

        verify(authService).createUser("admin@openpto.local", "pw", "Admin", Set.of(Role.USER, Role.ADMIN));
        assertThat(props.toString()).doesNotContain("pw");
    }

    @Test
    void bootstrap_disabled_doesNothing_andRaceIsTolerated() {
        new AdminBootstrap(new AppProperties.Admin(false, "a@b.c", "pw", "A"), users, authService).run(null);
        verifyNoInteractions(users, authService);

        given(users.existsByEmail("a@b.c")).willReturn(false);
        given(authService.createUser(anyString(), anyString(), anyString(), any())).willThrow(new ConflictException("dup"));
        new AdminBootstrap(new AppProperties.Admin(true, "a@b.c", "pw", "A"), users, authService).run(null);
        verify(users, never()).save(any());
    }
}
