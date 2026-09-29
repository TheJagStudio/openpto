package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GoodsServiceDto(
        @Schema(example = "30") @NotNull @Min(1) @Max(45) Integer niceClass,
        @Schema(example = "Coffee; coffee beans; tea") @NotBlank @Size(max = 10_000) String description) {

}
