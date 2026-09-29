package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Outcome of a bulk upsert; failed records do not affect the others")
public record BulkUpsertResponse(
        @Schema(example = "480") int inserted,
        @Schema(example = "18") int updated,
        List<Failure> failed) {

    public record Failure(
            @Schema(example = "US11234567B2", description = "Natural key, or #index when the key is missing") String id,
            @Schema(example = "title: must not be blank") String error) {
    }
}
