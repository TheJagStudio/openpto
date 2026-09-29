package gov.openpto.odp.dto;

import gov.openpto.odp.model.ApiTier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "API key verification result; all fields except valid are null when invalid")
public record VerifyKeyResponse(
        @Schema(example = "true") boolean valid,
        UUID keyId,
        UUID userId,
        @Schema(example = "FREE") ApiTier tier,
        @Schema(example = "120") Integer perMinute,
        @Schema(example = "20000") Integer perDay) {

    public static VerifyKeyResponse invalid() {
        return new VerifyKeyResponse(false, null, null, null, null, null);
    }

    public static VerifyKeyResponse valid(UUID keyId, UUID userId, ApiTier tier) {
        return new VerifyKeyResponse(true, keyId, userId, tier, tier.perMinute(), tier.perDay());
    }
}
