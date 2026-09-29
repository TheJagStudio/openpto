package gov.openpto.odp.service;

import gov.openpto.odp.config.AppConfig;
import gov.openpto.odp.dto.PageParams;
import gov.openpto.odp.dto.PageResponse;
import gov.openpto.odp.dto.PatentDetail;
import gov.openpto.odp.dto.PatentFacets;
import gov.openpto.odp.dto.PatentFilter;
import gov.openpto.odp.dto.PatentSummary;
import gov.openpto.odp.exception.NotFoundException;
import gov.openpto.odp.mapper.PatentMapper;
import gov.openpto.odp.model.Patent;
import gov.openpto.odp.repository.PatentFacetRepository;
import gov.openpto.odp.repository.PatentRepository;
import gov.openpto.odp.repository.spec.PatentSpecifications;
import gov.openpto.odp.repository.spec.SearchSort;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patent search (Postgres FTS + filters), facets and detail. Mapping happens inside the read-only
 * transaction so the page's assignee/inventor collections are batch-fetched (one query each).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PatentService {

    static final int TOP_ASSIGNEES = 10;

    private final PatentRepository patents;
    private final PatentFacetRepository facetRepository;
    private final PatentMapper mapper;

    public PageResponse<PatentSummary> search(PatentFilter filter, PageParams paging) {
        SearchSort sort = PatentSpecifications.sort(paging.sort(), filter);
        Page<Patent> page = patents.findAll(
                PatentSpecifications.matchingOrdered(filter, sort),
                PageRequest.of(paging.pageOrDefault(), paging.sizeOrDefault()));
        return PageResponse.of(page, mapper::toSummary);
    }

    @Cacheable(AppConfig.FACETS_CACHE)
    public PatentFacets facets(PatentFilter filter) {
        Specification<Patent> spec = PatentSpecifications.matching(filter);
        return new PatentFacets(
                facetRepository.countByAttribute(spec, "type", 10),
                facetRepository.countByAttribute(spec, "status", 10),
                facetRepository.countByFilingYear(spec),
                facetRepository.countByAttribute(spec, "cpcSection", 10),
                facetRepository.topAssignees(spec, TOP_ASSIGNEES));
    }

    public PatentDetail detail(String patentNumber) {
        String key = patentNumber.trim().toUpperCase(Locale.ROOT);
        return patents.findByPatentNumber(key)
                .map(mapper::toDetail)
                .orElseThrow(() -> NotFoundException.of("Patent", key));
    }
}
