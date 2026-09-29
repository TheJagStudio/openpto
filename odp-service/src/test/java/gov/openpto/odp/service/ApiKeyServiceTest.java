package gov.openpto.odp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.dto.ApiKeyResponse;
import gov.openpto.odp.dto.CreateApiKeyRequest;
import gov.openpto.odp.dto.VerifyKeyResponse;
import gov.openpto.odp.exception.ConflictException;
import gov.openpto.odp.exception.NotFoundException;
import gov.openpto.odp.mapper.AccountMapper;
import gov.openpto.odp.mapper.AccountMapperImpl;
import gov.openpto.odp.model.ApiKey;
import gov.openpto.odp.model.ApiTier;
import gov.openpto.odp.model.Role;
import gov.openpto.odp.model.User;
import gov.openpto.odp.repository.ApiKeyLookup;
import gov.openpto.odp.repository.ApiKeyRepository;
import gov.openpto.odp.repository.UserRepository;

import java.time.Clock;
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

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Mock
    ApiKeyRepository keys;

    @Mock
    UserRepository users;

    ApiKeyGenerator generator = new ApiKeyGenerator();
    AccountMapper mapper = new AccountMapperImpl();
    ApiKeyService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(keys, users, generator, mapper, new AppProperties.ApiKeys(5), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User user(Role... roles) {
        User u = new User();
        u.setId(userId);
        u.setRoles(EnumSet.copyOf(List.of(roles)));
        return u;
    }

    private ApiKey key(Instant revokedAt) {
        ApiKey k = new ApiKey();
        k.setId(UUID.randomUUID());
        k.setName("k");
        k.setKeyHash("old");
        k.setPrefix("opto_old1");
        k.setTier(ApiTier.FREE);
        k.setCreatedAt(NOW.minusSeconds(3600));
        k.setRevokedAt(revokedAt);
        return k;
    }

    @Test
    void create_underLimit_storesOnlyHashAndReturnsPlaintextOnce() {
        given(users.lockById(userId)).willReturn(Optional.of(user(Role.USER)));
        given(keys.countActiveForUser(userId)).willReturn(4L);

        ApiKeyResponse response = service.create(userId, new CreateApiKeyRequest("  notebook "));

        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        verify(keys).save(saved.capture());
        ApiKey stored = saved.getValue();
        assertThat(response.key()).startsWith("opto_").hasSize(45);
        assertThat(stored.getKeyHash()).isEqualTo(ApiKeyGenerator.sha256Hex(response.key())).hasSize(64);
        assertThat(stored.getPrefix()).isEqualTo(response.key().substring(0, 9)).isEqualTo(response.prefix());
        assertThat(stored.getName()).isEqualTo("notebook");
        assertThat(stored.getTier()).isEqualTo(ApiTier.FREE);
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.requestCount()).isZero();
    }

    @Test
    void create_adminUser_getsAdminTier() {
        given(users.lockById(userId)).willReturn(Optional.of(user(Role.USER, Role.ADMIN)));
        given(keys.countActiveForUser(userId)).willReturn(0L);

        assertThat(service.create(userId, new CreateApiKeyRequest("ops")).tier()).isEqualTo(ApiTier.ADMIN);
    }

    @Test
    void create_fiveActiveKeys_throwsConflict() {
        given(users.lockById(userId)).willReturn(Optional.of(user(Role.USER)));
        given(keys.countActiveForUser(userId)).willReturn(5L);

        assertThatThrownBy(() -> service.create(userId, new CreateApiKeyRequest("sixth")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("5 active API keys");
        verify(keys, never()).save(any());
    }

    @Test
    void create_unknownUser_throwsNotFound() {
        given(users.lockById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, new CreateApiKeyRequest("x"))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void list_mapsWithoutPlaintext() {
        given(keys.findAllForUser(userId)).willReturn(List.of(key(null)));

        assertThat(service.list(userId)).singleElement().satisfies(r -> assertThat(r.key()).isNull());
    }

    @Test
    void rotate_activeKey_replacesHashAndPrefix() {
        ApiKey k = key(null);
        given(keys.findForUser(k.getId(), userId)).willReturn(Optional.of(k));

        ApiKeyResponse response = service.rotate(userId, k.getId());

        assertThat(k.getKeyHash()).isEqualTo(ApiKeyGenerator.sha256Hex(response.key())).isNotEqualTo("old");
        assertThat(k.getPrefix()).isEqualTo(response.prefix()).isNotEqualTo("opto_old1");
        assertThat(k.getRotatedAt()).isEqualTo(NOW);
        assertThat(response.id()).isEqualTo(k.getId());
    }

    @Test
    void rotate_revokedKey_throwsConflict() {
        ApiKey k = key(NOW.minusSeconds(5));
        given(keys.findForUser(k.getId(), userId)).willReturn(Optional.of(k));

        assertThatThrownBy(() -> service.rotate(userId, k.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void rotate_otherUsersKey_throwsNotFound() {
        UUID id = UUID.randomUUID();
        given(keys.findForUser(id, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(userId, id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void revoke_activeKey_setsRevokedAt_andIsIdempotent() {
        ApiKey k = key(null);
        given(keys.findForUser(k.getId(), userId)).willReturn(Optional.of(k));

        service.revoke(userId, k.getId());
        assertThat(k.getRevokedAt()).isEqualTo(NOW);

        Instant first = k.getRevokedAt();
        service.revoke(userId, k.getId());
        assertThat(k.getRevokedAt()).isEqualTo(first);
    }

    @Test
    void verify_malformedKey_isInvalidWithoutDatabaseLookup() {
        assertThat(service.verify("not-a-key").valid()).isFalse();
        assertThat(service.verify(null).valid()).isFalse();
        assertThat(service.verify("opto_short").valid()).isFalse();
        verifyNoInteractions(keys);
    }

    @Test
    void verify_activeKey_returnsTierLimits() {
        String plaintext = generator.generate().plaintext();
        UUID keyId = UUID.randomUUID();
        given(keys.lookupByHash(ApiKeyGenerator.sha256Hex(plaintext)))
                .willReturn(Optional.of(new ApiKeyLookup(keyId, userId, ApiTier.FREE, null, true)));

        VerifyKeyResponse r = service.verify(plaintext);

        assertThat(r).isEqualTo(new VerifyKeyResponse(true, keyId, userId, ApiTier.FREE, 120, 20_000));
    }

    @Test
    void verify_revokedOrDisabledOrUnknown_isInvalid() {
        String revoked = generator.generate().plaintext();
        String disabled = generator.generate().plaintext();
        String unknown = generator.generate().plaintext();
        given(keys.lookupByHash(ApiKeyGenerator.sha256Hex(revoked)))
                .willReturn(Optional.of(new ApiKeyLookup(UUID.randomUUID(), userId, ApiTier.FREE, NOW, true)));
        given(keys.lookupByHash(ApiKeyGenerator.sha256Hex(disabled)))
                .willReturn(Optional.of(new ApiKeyLookup(UUID.randomUUID(), userId, ApiTier.FREE, null, false)));
        given(keys.lookupByHash(ApiKeyGenerator.sha256Hex(unknown))).willReturn(Optional.empty());

        assertThat(service.verify(revoked)).isEqualTo(VerifyKeyResponse.invalid());
        assertThat(service.verify(disabled).valid()).isFalse();
        assertThat(service.verify(unknown).valid()).isFalse();
    }

    @Test
    void generator_producesBase62KeysThatPassFormatCheck() {
        for (int i = 0; i < 50; i++) {
            ApiKeyGenerator.GeneratedKey g = generator.generate();
            assertThat(g.plaintext()).matches("^opto_[0-9A-Za-z]{40}$");
            assertThat(ApiKeyGenerator.looksLikeKey(g.plaintext())).isTrue();
            assertThat(g.toString()).doesNotContain(g.plaintext());
        }
        assertThat(ApiKeyGenerator.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        verify(keys, never()).lookupByHash(anyString());
    }
}
