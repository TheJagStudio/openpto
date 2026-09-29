package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UsageResponse(@Schema(example = "1234") long totalRequests, List<DailyUsage> daily,
                            List<KeyUsage> byKey) {

    public record DailyUsage(@Schema(example = "2026-09-27") LocalDate date, @Schema(example = "87") long requests) {
    }

    public record KeyUsage(UUID keyId, @Schema(example = "analytics notebook") String name,
                           @Schema(example = "640") long requests) {
    }
}
