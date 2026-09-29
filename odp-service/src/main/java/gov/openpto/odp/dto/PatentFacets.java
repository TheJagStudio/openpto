package gov.openpto.odp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Facet counts for the current patent filters (years = filing year)")
public record PatentFacets(
        List<FacetCount> types,
        List<FacetCount> statuses,
        List<FacetCount> years,
        List<FacetCount> cpcSections,
        List<FacetCount> topAssignees) {
}
