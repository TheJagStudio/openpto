package gov.openpto.fee.controller;

import gov.openpto.fee.config.OpenApiConfig;
import gov.openpto.fee.domain.FeeCategory;
import gov.openpto.fee.dto.ScheduleDetailResponse;
import gov.openpto.fee.dto.ScheduleSummaryResponse;
import gov.openpto.fee.service.FeeScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/schedules")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_SCHEDULES)
public class FeeScheduleController {

    private final FeeScheduleService service;

    @GetMapping
    @Operation(summary = "List fee schedules", description = "Newest first; `current` marks the schedule effective today.")
    public List<ScheduleSummaryResponse> list() {
        return service.list();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get a fee schedule with its items")
    @ApiResponse(responseCode = "200", description = "Schedule found")
    @ApiResponse(responseCode = "404", description = "Unknown schedule code")
    public ScheduleDetailResponse get(
            @Parameter(description = "Schedule code (e.g. FY2025) or `current`", example = "current")
            @PathVariable String code,
            @Parameter(description = "Only items of this category") @RequestParam(required = false) FeeCategory category) {
        return service.get(code, category);
    }
}
