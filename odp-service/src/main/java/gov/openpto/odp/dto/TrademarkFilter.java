package gov.openpto.odp.dto;

import gov.openpto.odp.model.TrademarkStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/** Trademark search filters, bound from query parameters. All optional. */
public record TrademarkFilter(
        @Parameter(description = "Full-text query over mark text, owner and goods/services", example = "coffee")
        @Size(max = 500) String q,
        @Parameter(description = "Status") TrademarkStatus status,
        @Parameter(description = "Nice class (1-45)", example = "30") @Min(1) @Max(45) Integer niceClass,
        @Parameter(description = "Owner name contains (case-insensitive)", example = "heron") @Size(max = 200) String owner,
        @Parameter(description = "Filed on/after (yyyy-MM-dd)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filedFrom,
        @Parameter(description = "Filed on/before (yyyy-MM-dd)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filedTo) {

    public static TrademarkFilter empty() {
        return new TrademarkFilter(null, null, null, null, null, null);
    }

    public boolean hasQuery() {
        return q != null && !q.isBlank();
    }
}
