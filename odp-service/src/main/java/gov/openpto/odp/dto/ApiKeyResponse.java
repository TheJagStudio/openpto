package gov.openpto.odp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.openpto.odp.model.ApiTier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        @Schema(example = "analytics notebook") String name,
        @Schema(example = "opto_ab12") String prefix,
        @Schema(example = "FREE") ApiTier tier,
        Instant createdAt,
        Instant lastUsedAt,
        Instant revokedAt,
        @Schema(example = "42") long requestCount,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Plaintext key; only returned by create/rotate", example = "opto_ab12Cd34Ef56Gh78Ij90Kl12Mn34Op56Qr78St90")
        String key) {

    public ApiKeyResponse withKey(String plaintext) {
        return new ApiKeyResponse(id, name, prefix, tier, createdAt, lastUsedAt, revokedAt, requestCount, plaintext);
    }

    @Override
    public String toString() {
        return "ApiKeyResponse[id=" + id + ", prefix=" + prefix + "]";
    }
}
