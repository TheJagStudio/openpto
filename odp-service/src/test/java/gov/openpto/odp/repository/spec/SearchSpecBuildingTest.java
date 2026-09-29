package gov.openpto.odp.repository.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.openpto.odp.dto.PatentFilter;
import gov.openpto.odp.dto.TrademarkFilter;
import gov.openpto.odp.exception.BadRequestException;
import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.PatentType;
import gov.openpto.odp.model.TrademarkStatus;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SearchSpecBuildingTest {

    @Test
    void sort_defaultsToRelevanceWithQuery_andFilingDateWithout() {
        assertThat(PatentSpecifications.sort(null, filter("battery"))).isEqualTo(new SearchSort("relevance", false));
        assertThat(PatentSpecifications.sort(" ", PatentFilter.empty())).isEqualTo(new SearchSort("filingDate", false));
    }

    @Test
    void sort_parsesDirection_andIgnoresRelevanceWithoutQuery() {
        assertThat(PatentSpecifications.sort("grantDate,ASC", PatentFilter.empty())).isEqualTo(new SearchSort("grantDate", true));
        assertThat(PatentSpecifications.sort("patentNumber", PatentFilter.empty())).isEqualTo(new SearchSort("patentNumber", false));
        assertThat(PatentSpecifications.sort("relevance,desc", PatentFilter.empty())).isEqualTo(new SearchSort("filingDate", false));
        assertThat(TrademarkSpecifications.sort("registrationDate,desc", TrademarkFilter.empty()))
                .isEqualTo(new SearchSort("registrationDate", false));
    }

    @Test
    void sort_unknownField_isBadRequest() {
        assertThatThrownBy(() -> PatentSpecifications.sort("title,asc", PatentFilter.empty()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("filingDate, grantDate, patentNumber, relevance");
    }

    @Test
    void likePatterns_escapeWildcards() {
        assertThat(SpecSupport.containsPattern(" 50%_Off\\ ")).isEqualTo("%50\\%\\_off\\\\%");
        assertThat(SpecSupport.prefixPattern("G06F")).isEqualTo("G06F%");
        assertThat(PatentSpecifications.normalizeCpc("g06f 16/245")).isEqualTo("G06F16/245");
    }

    @Test
    void patentSql_unfiltered_isTrueWithDefaultOrder() {
        SqlFilters.SqlQuery q = SqlFilters.patents(PatentFilter.empty(), new SearchSort("filingDate", false));

        assertThat(q.where()).isEqualTo("TRUE");
        assertThat(q.orderBy()).isEqualTo("p.filing_date DESC NULLS LAST, p.id DESC");
        assertThat(q.params()).isEmpty();
    }

    @Test
    void patentSql_allFilters_areParameterizedInOrder() {
        PatentFilter f = new PatentFilter("solid state", PatentType.UTILITY, PatentStatus.GRANTED, "h01m", "Acme_", "lee",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), LocalDate.of(2021, 1, 1), null);

        SqlFilters.SqlQuery q = SqlFilters.patents(f, new SearchSort("relevance", false));

        assertThat(q.where())
                .contains("p.search_vector @@ websearch_to_tsquery('english', ?)")
                .contains("p.type = ?")
                .contains("c.code LIKE ? ESCAPE")
                .contains("lower(a.name) LIKE ?")
                .contains("lower(i.name) LIKE ?")
                .contains("p.filing_date >= ?")
                .contains("p.filing_date <= ?")
                .contains("p.grant_date >= ?")
                .doesNotContain("p.grant_date <=")
                .doesNotContain("Acme");
        assertThat(q.orderBy()).startsWith("ts_rank_cd(p.search_vector, websearch_to_tsquery('english', ?)) DESC");
        assertThat(q.params()).containsExactly("solid state", "UTILITY", "GRANTED", "H01M%", "%acme\\_%", "%lee%",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), LocalDate.of(2021, 1, 1), "solid state");
    }

    @Test
    void trademarkSql_filtersAndSort() {
        TrademarkFilter f = new TrademarkFilter("coffee", TrademarkStatus.LIVE_REGISTERED, 30, "heron", null, LocalDate.of(2024, 1, 1));

        SqlFilters.SqlQuery q = SqlFilters.trademarks(f, new SearchSort("serialNumber", true));

        assertThat(q.where()).contains("t.nice_classes @> ARRAY[?]::integer[]").contains("lower(t.owner_name) LIKE ?")
                .contains("t.filing_date <= ?");
        assertThat(q.orderBy()).isEqualTo("t.serial_number ASC NULLS LAST, t.id DESC");
        assertThat(q.params()).containsExactly("coffee", "LIVE_REGISTERED", 30, "%heron%", LocalDate.of(2024, 1, 1));

        SqlFilters.SqlQuery relevance = SqlFilters.trademarks(f, new SearchSort("relevance", false));
        assertThat(relevance.orderBy()).startsWith("ts_rank_cd(t.search_vector");
        assertThat(relevance.params()).last().isEqualTo("coffee");
    }

    private static PatentFilter filter(String q) {
        return new PatentFilter(q, null, null, null, null, null, null, null, null, null);
    }
}
