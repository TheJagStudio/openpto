package gov.openpto.odp.controller;

import gov.openpto.odp.dto.PageParams;
import gov.openpto.odp.dto.PageResponse;
import gov.openpto.odp.dto.TrademarkDetail;
import gov.openpto.odp.dto.TrademarkFilter;
import gov.openpto.odp.dto.TrademarkSummary;
import gov.openpto.odp.service.TrademarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trademarks")
@Tag(name = "Trademarks")
@RequiredArgsConstructor
public class TrademarkController {

    private final TrademarkService trademarks;

    @GetMapping
    @Operation(summary = "Search trademarks", description = "Full-text over mark text, owner and goods/services; sort by relevance, filingDate, registrationDate or serialNumber.")
    public PageResponse<TrademarkSummary> search(
            @Valid @ParameterObject TrademarkFilter filter, @Valid @ParameterObject PageParams paging) {
        return trademarks.search(filter, paging);
    }

    @GetMapping("/{serialNumber}")
    @Operation(summary = "TSDR-style trademark detail")
    @ApiResponse(responseCode = "404", description = "Unknown serial number")
    public TrademarkDetail detail(
            @PathVariable @Pattern(regexp = "^[0-9]{6,12}$", message = "must be a serial number such as 97123456")
            String serialNumber) {
        return trademarks.detail(serialNumber);
    }
}
