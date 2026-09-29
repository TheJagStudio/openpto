package gov.openpto.odp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import gov.openpto.odp.dto.FacetCount;
import gov.openpto.odp.dto.PageParams;
import gov.openpto.odp.dto.PageResponse;
import gov.openpto.odp.dto.PatentFacets;
import gov.openpto.odp.dto.PatentFilter;
import gov.openpto.odp.dto.PatentSummary;
import gov.openpto.odp.dto.StatsResponse;
import gov.openpto.odp.dto.TrademarkFilter;
import gov.openpto.odp.dto.TrademarkSummary;
import gov.openpto.odp.exception.BadRequestException;
import gov.openpto.odp.exception.NotFoundException;
import gov.openpto.odp.mapper.PatentMapperImpl;
import gov.openpto.odp.mapper.TrademarkMapperImpl;
import gov.openpto.odp.model.Party;
import gov.openpto.odp.model.Patent;
import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.model.Trademark;
import gov.openpto.odp.model.TrademarkStatus;
import gov.openpto.odp.repository.PatentFacetRepository;
import gov.openpto.odp.repository.PatentRepository;
import gov.openpto.odp.repository.TrademarkRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class SearchServicesTest {

    @Mock
    PatentRepository patentRepo;

    @Mock
    PatentFacetRepository facetRepo;

    @Mock
    TrademarkRepository trademarkRepo;

    @Test
    @SuppressWarnings("unchecked")
    void patentSearch_mapsPageWithRequestedPaging() {
        Patent p = new Patent();
        p.setPatentNumber("US1B2");
        p.setTitle("t");
        p.setAbstractText("word ".repeat(100));
        p.setAssignees(List.of(new Party("Acme", null, null, "US")));
        given(patentRepo.findAll(any(Specification.class), eq(PageRequest.of(2, 5))))
                .willReturn(new PageImpl<>(List.of(p), PageRequest.of(2, 5), 11));
        PatentService service = new PatentService(patentRepo, facetRepo, new PatentMapperImpl());

        PageResponse<PatentSummary> page = service.search(PatentFilter.empty(), new PageParams(2, 5, "grantDate,asc"));

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(11);
        assertThat(page.totalPages()).isEqualTo(3);
        PatentSummary s = page.content().getFirst();
        assertThat(s.assignees()).containsExactly("Acme");
        assertThat(s.inventors()).isEmpty();
        assertThat(s.abstractSnippet()).endsWith("…").hasSizeLessThan(250);
    }

    @Test
    void patentSearch_badSortField_isRejectedBeforeQuerying() {
        PatentService service = new PatentService(patentRepo, facetRepo, new PatentMapperImpl());

        assertThatThrownBy(() -> service.search(PatentFilter.empty(), new PageParams(null, null, "title,asc")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void patentFacets_collectsAllFacetGroups() {
        given(facetRepo.countByAttribute(any(Specification.class), eq("type"), anyInt())).willReturn(List.of(new FacetCount("UTILITY", 3)));
        given(facetRepo.countByAttribute(any(Specification.class), eq("status"), anyInt())).willReturn(List.of(new FacetCount("GRANTED", 2)));
        given(facetRepo.countByAttribute(any(Specification.class), eq("cpcSection"), anyInt())).willReturn(List.of(new FacetCount("H", 3)));
        given(facetRepo.countByFilingYear(any(Specification.class))).willReturn(List.of(new FacetCount("2020", 3)));
        given(facetRepo.topAssignees(any(Specification.class), eq(10))).willReturn(List.of(new FacetCount("Acme", 3)));
        PatentService service = new PatentService(patentRepo, facetRepo, new PatentMapperImpl());

        PatentFacets f = service.facets(PatentFilter.empty());

        assertThat(f.types()).containsExactly(new FacetCount("UTILITY", 3));
        assertThat(f.statuses()).hasSize(1);
        assertThat(f.years()).hasSize(1);
        assertThat(f.cpcSections()).hasSize(1);
        assertThat(f.topAssignees()).hasSize(1);
    }

    @Test
    void patentDetail_normalizesKey_andMapsFullAbstract() {
        Patent p = new Patent();
        p.setPatentNumber("US1B2");
        p.setAbstractText("word ".repeat(100).trim());
        p.setStatus(PatentStatus.GRANTED);
        given(patentRepo.findByPatentNumber("US1B2")).willReturn(Optional.of(p));
        PatentService service = new PatentService(patentRepo, facetRepo, new PatentMapperImpl());

        var detail = service.detail(" us1b2 ");

        assertThat(detail.abstractText()).hasSize(499);
        assertThat(detail.abstractSnippet()).endsWith("…");
        assertThat(detail.claims()).isEmpty();
    }

    @Test
    void patentDetail_unknown_throwsNotFound() {
        given(patentRepo.findByPatentNumber("US0")).willReturn(Optional.empty());
        PatentService service = new PatentService(patentRepo, facetRepo, new PatentMapperImpl());

        assertThatThrownBy(() -> service.detail("US0")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void trademarkSearch_andDetail() {
        Trademark t = new Trademark();
        t.setSerialNumber("97123456");
        t.setMarkText("MARK");
        t.setNiceClasses(new Integer[]{9, 42});
        given(trademarkRepo.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(t), PageRequest.of(0, 20), 1));
        given(trademarkRepo.findBySerialNumber("97123456")).willReturn(Optional.of(t));
        given(trademarkRepo.findBySerialNumber("1")).willReturn(Optional.empty());
        TrademarkService service = new TrademarkService(trademarkRepo, new TrademarkMapperImpl());

        PageResponse<TrademarkSummary> page = service.search(TrademarkFilter.empty(), PageParams.defaults());

        assertThat(page.content().getFirst().niceClasses()).containsExactly(9, 42);
        assertThat(service.detail(" 97123456 ").goodsAndServices()).isEmpty();
        assertThatThrownBy(() -> service.detail("1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void stats_combinesCountsAndLatestIngest() {
        Instant p = Instant.parse("2026-01-01T00:00:00Z");
        Instant t = Instant.parse("2026-02-01T00:00:00Z");
        given(patentRepo.countByFilingYear()).willReturn(List.<Object[]>of(new Object[]{2020, 4L}, new Object[]{2021, 6L}));
        given(trademarkRepo.countByStatus()).willReturn(List.<Object[]>of(new Object[]{TrademarkStatus.LIVE_PENDING, 2L}));
        given(patentRepo.lastUpdatedAt(RecordSource.INGEST)).willReturn(Optional.of(p));
        given(trademarkRepo.lastUpdatedAt(RecordSource.INGEST)).willReturn(Optional.of(t));
        given(patentRepo.count()).willReturn(10L);
        given(trademarkRepo.count()).willReturn(2L);

        StatsResponse s = new StatsService(patentRepo, trademarkRepo).stats();

        assertThat(s.patents()).isEqualTo(10);
        assertThat(s.trademarks()).isEqualTo(2);
        assertThat(s.patentsByYear()).extracting(y -> y.year()).containsExactly(2020, 2021);
        assertThat(s.trademarksByStatus()).containsExactly(new FacetCount("LIVE_PENDING", 2));
        assertThat(s.lastIngestAt()).isEqualTo(t);
    }

    @Test
    void stats_noIngestYet_hasNullLastIngest() {
        given(patentRepo.countByFilingYear()).willReturn(List.of());
        given(trademarkRepo.countByStatus()).willReturn(List.of());
        given(patentRepo.lastUpdatedAt(RecordSource.INGEST)).willReturn(Optional.empty());
        given(trademarkRepo.lastUpdatedAt(RecordSource.INGEST)).willReturn(Optional.empty());

        assertThat(new StatsService(patentRepo, trademarkRepo).stats().lastIngestAt()).isNull();
    }
}
