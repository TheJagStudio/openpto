package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A facet bucket")
public record FacetCount(@Schema(example = "GRANTED") String value, @Schema(example = "3120") long count) {

}
