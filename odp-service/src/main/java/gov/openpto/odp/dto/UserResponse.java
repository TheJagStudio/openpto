package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        @Schema(example = "3f1c2a7e-5b8d-4a51-9a51-0c1d2e3f4a5b") UUID id,
        @Schema(example = "ada@example.com") String email,
        @Schema(example = "Ada Lovelace") String displayName,
        @Schema(example = "[\"USER\"]") List<String> roles,
        Instant createdAt) {
}
