package gov.openpto.odp.dto;

import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.PatentType;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/** Patent search filters, bound from query parameters. All optional. */
public record PatentFilter(
        @Parameter(description = "Full-text query (web search syntax: \"quoted phrase\", -exclude, or)", example = "solid state battery")
        @Size(max = 500) String q,
        @Parameter(description = "Patent type") PatentType type,
        @Parameter(description = "Patent status") PatentStatus status,
        @Parameter(description = "CPC code prefix", example = "H01M")
        @Pattern(regexp = "^[A-Za-z0-9/ ]{1,32}$", message = "must be a CPC code prefix such as G06F or H01M10/05")
        String cpc,
        @Parameter(description = "Assignee name contains (case-insensitive)", example = "northwind") @Size(max = 200) String assignee,
        @Parameter(description = "Inventor name contains (case-insensitive)", example = "delgado") @Size(max = 200) String inventor,
        @Parameter(description = "Filed on/after (yyyy-MM-dd)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filedFrom,
        @Parameter(description = "Filed on/before (yyyy-MM-dd)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filedTo,
        @Parameter(description = "Granted on/after (yyyy-MM-dd)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate grantedFrom,
        @Parameter(description = "Granted on/before (yyyy-MM-dd)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate grantedTo) {

    public static PatentFilter empty() {
        return new PatentFilter(null, null, null, null, null, null, null, null, null, null);
    }

    public boolean hasQuery() {
        return q != null && !q.isBlank();
    }
}
