package gov.openpto.ingest.dto;

import gov.openpto.ingest.transform.DocumentFormat;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A downloadable sample USPTO bulk file")
public record SampleResponse(
        @Schema(example = "ipg250107-sample.xml") String name,
        @Schema(example = "Patent grant bulk file with 5 concatenated us-patent-grant documents") String description,
        @Schema(example = "US_PATENT_GRANT") DocumentFormat format,
        @Schema(example = "21873") long sizeBytes) {
}