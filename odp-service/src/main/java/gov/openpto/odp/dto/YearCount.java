package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record YearCount(@Schema(example = "2021") int year, @Schema(example = "412") long count) {

}
