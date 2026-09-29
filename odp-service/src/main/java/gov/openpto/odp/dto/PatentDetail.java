package gov.openpto.odp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.PatentType;
import gov.openpto.odp.model.RecordSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Full patent record")
public record PatentDetail(
        @Schema(example = "US11234567B2") String patentNumber,
        @Schema(example = "17/123,456") String applicationNumber,
        @Schema(example = "Solid-state battery cell with layered sulfide electrolyte") String title,
        @Schema(example = "UTILITY") PatentType type,
        @Schema(example = "GRANTED") PatentStatus status,
        @Schema(example = "2020-03-14") LocalDate filingDate,
        @Schema(example = "2022-02-01") LocalDate grantDate,
        @Schema(example = "H01M10/0562") String primaryCpc,
        String abstractSnippet,
        RecordSource source,
        @JsonProperty("abstract") @Schema(name = "abstract", example = "A battery cell includes ...") String abstractText,
        List<ClaimDto> claims,
        @Schema(example = "[\"H01M10/0562\", \"H01M4/13\"]") List<String> cpcCodes,
        List<CitationDto> citations,
        List<PartyDto> inventors,
        List<PartyDto> assignees,
        @Schema(example = "2019-03-15") LocalDate priorityDate,
        @Schema(example = "2040-03-14") LocalDate expirationDate,
        @Schema(example = "Laura Chen") String examiner,
        @Schema(example = "1729") String artUnit,
        String ingestJobId,
        Instant updatedAt) {
}
