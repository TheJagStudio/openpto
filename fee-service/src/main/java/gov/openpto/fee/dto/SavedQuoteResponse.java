package gov.openpto.fee.dto;

import gov.openpto.fee.domain.EntitySize;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

@Schema(description = "A saved quote: FeeQuote fields recomputed from the stored, normalized request.")
public record SavedQuoteResponse(
        @Schema(example = "3f1c2a8e-7c1b-4d5e-9a0b-2b7c1f0d9e11") UUID id,
        @Schema(example = "PATENT_FILING") QuoteKind kind,
        @Schema(example = "Widget patent - small entity", nullable = true) String label,
        @Schema(description = "Normalized request (defaults and date filled in)") JsonNode request,
        @Schema(example = "FY2025") String scheduleCode,
        String scheduleName,
        @Schema(nullable = true) EntitySize entitySize,
        List<LineItemResponse> lineItems,
        List<SubtotalResponse> subtotals,
        @Schema(example = "3040.00") BigDecimal total,
        @Schema(example = "USD") String currency,
        List<String> notes,
        List<String> warnings,
        @Schema(nullable = true, description = "Window details, only for kind MAINTENANCE") MaintenanceResponse maintenance,
        Instant createdAt) {
}
