package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VerifyKeyRequest(
        @Schema(example = "opto_ab12Cd34Ef56Gh78Ij90Kl12Mn34Op56Qr78St90") @NotNull @Size(max = 200) String key) {

    @Override
    public String toString() {
        return "VerifyKeyRequest[***]";
    }
}
