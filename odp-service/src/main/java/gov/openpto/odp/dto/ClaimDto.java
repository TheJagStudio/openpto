package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A patent claim")
public record ClaimDto(
        @Schema(example = "2") @NotNull @Min(1) @Max(1000) Integer number,
        @Schema(example = "The method of claim 1, wherein the electrolyte comprises a lithium salt.")
        @NotBlank @Size(max = 20_000) String text,
        @Schema(example = "false", description = "Derived on upsert: true when dependsOn is null") Boolean independent,
        @Schema(example = "1") @Min(1) Integer dependsOn) {
}
