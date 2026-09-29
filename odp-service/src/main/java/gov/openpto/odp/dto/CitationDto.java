package gov.openpto.odp.dto;

import gov.openpto.odp.model.CitedBy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A cited patent")
public record CitationDto(
        @Schema(example = "US10987654B2") @NotBlank @Size(max = 32) String patentNumber,
        @Schema(example = "EXAMINER") @NotNull CitedBy citedBy) {

}
