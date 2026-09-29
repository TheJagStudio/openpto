package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Batched per-key usage counters flushed by the gateway")
public record UsageReportRequest(@NotNull @Size(max = 10_000) List<@Valid @NotNull Entry> entries) {

    public record Entry(
            @NotNull UUID keyId,
            @Schema(example = "2026-09-28") @NotNull LocalDate date,
            @Schema(example = "57") @NotNull @Min(0) @Max(1_000_000_000) Long count) {
    }
}
