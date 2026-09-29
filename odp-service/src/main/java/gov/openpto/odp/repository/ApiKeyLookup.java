package gov.openpto.odp.repository;

import gov.openpto.odp.model.ApiTier;

import java.time.Instant;
import java.util.UUID;

/** Projection used by API key verification. */
public record ApiKeyLookup(UUID keyId, UUID userId, ApiTier tier, Instant revokedAt, boolean userEnabled) {
}
