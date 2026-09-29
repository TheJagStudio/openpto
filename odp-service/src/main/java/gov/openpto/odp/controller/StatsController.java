package gov.openpto.odp.controller;

import gov.openpto.odp.dto.StatsResponse;
import gov.openpto.odp.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService stats;

    @GetMapping
    @Operation(summary = "Record counts, patents by filing year, trademarks by status, last ingest time")
    public StatsResponse stats() {
        return stats.stats();
    }
}
