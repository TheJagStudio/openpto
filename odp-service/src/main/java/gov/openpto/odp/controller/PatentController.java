package gov.openpto.odp.controller;

import gov.openpto.odp.dto.PageParams;
import gov.openpto.odp.dto.PageResponse;
import gov.openpto.odp.dto.PatentDetail;
import gov.openpto.odp.dto.PatentFacets;
import gov.openpto.odp.dto.PatentFilter;
import gov.openpto.odp.dto.PatentSummary;
import gov.openpto.odp.service.PatentService;
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
@RequestMapping("/api/v1/patents")
@Tag(name = "Patents")
@RequiredArgsConstructor
public class PatentController {

    private final PatentService patents;

    @GetMapping
    @Operation(summary = "Search patents", description = "Full-text search (websearch syntax) with filters; sort by relevance, grantDate, filingDate or patentNumber.")
    @ApiResponse(responseCode = "200", description = "A page of patent summaries")
    @ApiResponse(responseCode = "400", description = "Invalid filter or paging parameter")
    public PageResponse<PatentSummary> search(
            @Valid @ParameterObject PatentFilter filter, @Valid @ParameterObject PageParams paging) {
        return patents.search(filter, paging);
    }

    @GetMapping("/facets")
    @Operation(summary = "Facet counts for the current filters")
    public PatentFacets facets(@Valid @ParameterObject PatentFilter filter) {
        return patents.facets(filter);
    }

    @GetMapping("/{patentNumber}")
    @Operation(summary = "Patent detail", description = "Claims, CPC codes, citations, parties and dates.")
    @ApiResponse(responseCode = "404", description = "Unknown patent number")
    public PatentDetail detail(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9-]{1,32}$", message = "must be a patent number such as US11234567B2")
            String patentNumber) {
        return patents.detail(patentNumber);
    }
}
