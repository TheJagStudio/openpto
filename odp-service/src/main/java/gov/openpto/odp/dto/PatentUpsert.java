package gov.openpto.odp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.PatentType;
import gov.openpto.odp.model.RecordSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Patent record accepted by the internal bulk upsert (and produced by the seed generator).
 * Keyed by {@code patentNumber}; when absent it is derived from {@code applicationNumber}.
 * {@code status}, {@code expirationDate} and {@code primaryCpc} are derived when omitted.
 */
@Schema(description = "Patent upsert record")
public record PatentUpsert(
        @Schema(example = "US11234567B2") @Size(max = 32) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "must be alphanumeric")
        String patentNumber,
        @Schema(example = "17/123,456") @Size(max = 32) String applicationNumber,
        @Schema(example = "Solid-state battery cell") @NotBlank @Size(max = 4000) String title,
        @JsonProperty("abstract") @Schema(name = "abstract") @Size(max = 100_000) String abstractText,
        @NotNull PatentType type,
        PatentStatus status,
        @NotNull LocalDate filingDate,
        LocalDate grantDate,
        LocalDate priorityDate,
        LocalDate expirationDate,
        @Size(max = 32) String primaryCpc,
        @Size(max = 200) List<@NotBlank @Size(max = 32) String> cpcCodes,
        @Size(max = 1000) List<@Valid @NotNull ClaimDto> claims,
        @Size(max = 200) List<@Valid @NotNull PartyDto> inventors,
        @Size(max = 100) List<@Valid @NotNull PartyDto> assignees,
        @Size(max = 2000) List<@Valid @NotNull CitationDto> citations,
        @Size(max = 200) String examiner,
        @Size(max = 16) String artUnit,
        @Size(max = 64) String ingestJobId,
        @Schema(example = "INGEST", description = "Ignored on the internal endpoint (always INGEST)") RecordSource source) {
}
