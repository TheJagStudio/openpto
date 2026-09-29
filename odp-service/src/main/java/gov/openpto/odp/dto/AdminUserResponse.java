package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String displayName,
        List<String> roles,
        boolean enabled,
        Instant createdAt,
        Instant lastLoginAt,
        Instant lockedUntil,
        @Schema(example = "2") long activeKeys,
        @Schema(example = "3") long totalKeys) {
}
