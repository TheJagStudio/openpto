package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TrademarkEventDto(
        @Schema(example = "2021-06-05") @NotNull LocalDate date,
        @Schema(example = "NWAP") @NotBlank @Size(max = 16) String code,
        @Schema(example = "NEW APPLICATION ENTERED IN TRAM") @NotBlank @Size(max = 500) String description) {
}
