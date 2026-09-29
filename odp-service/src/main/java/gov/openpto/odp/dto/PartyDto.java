package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Inventor or assignee")
public record PartyDto(
        @Schema(example = "Northwind Energy Systems, Inc.") @NotBlank @Size(max = 300) String name,
        @Schema(example = "Austin") @Size(max = 120) String city,
        @Schema(example = "TX") @Size(max = 60) String state,
        @Schema(example = "US") @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166 alpha-2 code") String country) {

}
