package gov.openpto.fee.dto;

import gov.openpto.fee.domain.EntitySize;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Maintenance-fee request for a granted utility patent.")
public record MaintenanceRequest(
        @NotNull @Schema(example = "SMALL") EntitySize entitySize,
        @NotNull @Schema(example = "2021-06-15") LocalDate grantDate,
        @Schema(example = "2025-02-01", description = "Evaluation date; defaults to today (US Eastern)") LocalDate asOfDate,
        @Size(max = 3)
        @ArraySchema(schema = @Schema(allowableValues = {"3.5", "7.5", "11.5"}),
                arraySchema = @Schema(description = "Optional payment history. When omitted, windows whose grace "
                        + "period has ended are assumed paid; when supplied, a passed stage not listed is EXPIRED."))
        List<@NotNull @Pattern(regexp = "3\\.5|7\\.5|11\\.5", message = "must be one of 3.5, 7.5, 11.5") String> paidStages) {

    public MaintenanceRequest withAsOfDate(LocalDate date) {
        return new MaintenanceRequest(entitySize, grantDate, date, paidStages);
    }
}
