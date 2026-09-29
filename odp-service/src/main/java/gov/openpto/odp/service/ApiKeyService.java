package gov.openpto.odp.service;

import gov.openpto.odp.config.AppProperties;
import gov.openpto.odp.dto.ApiKeyResponse;
import gov.openpto.odp.dto.CreateApiKeyRequest;
import gov.openpto.odp.dto.VerifyKeyResponse;
import gov.openpto.odp.exception.ConflictException;
import gov.openpto.odp.exception.NotFoundException;
import gov.openpto.odp.mapper.AccountMapper;
import gov.openpto.odp.model.ApiKey;
import gov.openpto.odp.model.ApiTier;
import gov.openpto.odp.model.Role;
import gov.openpto.odp.model.User;
import gov.openpto.odp.repository.ApiKeyRepository;
import gov.openpto.odp.repository.UserRepository;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API key lifecycle (create / list / rotate / revoke) and the gateway's verify lookup. */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository keys;
    private final UserRepository users;
    private final ApiKeyGenerator generator;
    private final AccountMapper mapper;
    private final AppProperties.ApiKeys props;
    private final Clock clock;

    public List<ApiKeyResponse> list(UUID userId) {
        return mapper.toResponses(keys.findAllForUser(userId));
    }

    @Transactional
    public ApiKeyResponse create(UUID userId, CreateApiKeyRequest request) {
        // Row lock on the user serializes concurrent creates, so the max-active check cannot be raced.
        User user = users.lockById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        if (keys.countActiveForUser(userId) >= props.maxActivePerUser()) {
            throw new ConflictException("A maximum of " + props.maxActivePerUser()
                    + " active API keys is allowed; revoke one before creating another");
        }
        ApiKeyGenerator.GeneratedKey generated = generator.generate();
        ApiKey key = new ApiKey();
        key.setId(UUID.randomUUID());
        key.setUser(user);
        key.setName(request.name().trim());
        key.setKeyHash(generated.hash());
        key.setPrefix(generated.prefix());
        key.setTier(user.getRoles().contains(Role.ADMIN) ? ApiTier.ADMIN : ApiTier.FREE);
        key.setCreatedAt(clock.instant());
        keys.save(key);
        log.info("User {} created API key {} ({})", userId, key.getId(), key.getPrefix());
        return mapper.toResponse(key).withKey(generated.plaintext());
    }

    @Transactional
    public ApiKeyResponse rotate(UUID userId, UUID keyId) {
        ApiKey key = ownedKey(userId, keyId);
        if (!key.isActive()) {
            throw new ConflictException("A revoked API key cannot be rotated");
        }
        ApiKeyGenerator.GeneratedKey generated = generator.generate();
        key.setKeyHash(generated.hash());
        key.setPrefix(generated.prefix());
        key.setRotatedAt(clock.instant());
        log.info("User {} rotated API key {}", userId, keyId);
        return mapper.toResponse(key).withKey(generated.plaintext());
    }

    /** Idempotent: revoking an already revoked key is a no-op. */
    @Transactional
    public void revoke(UUID userId, UUID keyId) {
        ApiKey key = ownedKey(userId, keyId);
        if (key.isActive()) {
            key.setRevokedAt(clock.instant());
            log.info("User {} revoked API key {}", userId, keyId);
        }
    }

    /** Single unique-index lookup by SHA-256 hash; the gateway caches the result. */
    public VerifyKeyResponse verify(String plaintext) {
        if (!ApiKeyGenerator.looksLikeKey(plaintext)) {
            return VerifyKeyResponse.invalid();
        }
        return keys.lookupByHash(ApiKeyGenerator.sha256Hex(plaintext))
                .filter(k -> k.revokedAt() == null && k.userEnabled())
                .map(k -> VerifyKeyResponse.valid(k.keyId(), k.userId(), k.tier()))
                .orElseGet(VerifyKeyResponse::invalid);
    }

    private ApiKey ownedKey(UUID userId, UUID keyId) {
        // Keys of other users are reported as missing (no ownership oracle).
        return keys.findForUser(keyId, userId).orElseThrow(() -> NotFoundException.of("API key", keyId));
    }
}
