package gov.openpto.fee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

@Schema(description = "Save a quote. The server recomputes it from `request`; client totals are never trusted.")
public record CreateQuoteRequest(
        @NotNull @Schema(example = "PATENT_FILING") QuoteKind kind,
        @NotNull @Schema(description = "PatentFilingRequest, MaintenanceRequest or TrademarkFeeRequest (per kind)",
                example = "{\"applicationType\":\"UTILITY\",\"entitySize\":\"SMALL\",\"totalClaims\":25,\"independentClaims\":5}")
        JsonNode request,
        @Size(max = 200) @Schema(example = "Widget patent - small entity") String label) {
}
