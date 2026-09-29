package gov.openpto.odp.dto;

import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.PatentType;
import gov.openpto.odp.model.RecordSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Patent search hit")
public record PatentSummary(
        @Schema(example = "US11234567B2") String patentNumber,
        @Schema(example = "17/123,456") String applicationNumber,
        @Schema(example = "Solid-state battery cell with layered sulfide electrolyte") String title,
        @Schema(example = "UTILITY") PatentType type,
        @Schema(example = "GRANTED") PatentStatus status,
        @Schema(example = "2020-03-14") LocalDate filingDate,
        @Schema(example = "2022-02-01") LocalDate grantDate,
        @Schema(example = "H01M10/0562") String primaryCpc,
        @Schema(example = "[\"Northwind Energy Systems, Inc.\"]") List<String> assignees,
        @Schema(example = "[\"Maria Delgado\", \"Kenji Watanabe\"]") List<String> inventors,
        @Schema(example = "A battery cell includes a cathode, an anode and a layered sulfide electrolyte...")
        String abstractSnippet,
        @Schema(example = "SEED") RecordSource source) {
}
