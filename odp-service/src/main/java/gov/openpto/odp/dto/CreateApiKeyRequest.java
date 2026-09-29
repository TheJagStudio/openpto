package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(@Schema(example = "analytics notebook") @NotBlank @Size(max = 100) String name) {

}
