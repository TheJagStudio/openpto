package gov.openpto.odp.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Bulk upsert (insert + update) → search with FTS/filters/paging → facets → detail → exports. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataFlowIT extends IntegrationTestBase {

    @Autowired
    EntityManagerFactory emf;

    final String word = uniqueWord();
    final String assigneeToken = uniqueWord();
    final String p1 = "USIT" + digits(8) + "B2";
    final String p2 = "USIT" + digits(8) + "B2";
    final String p3 = "USITD" + digits(7) + "S";
    final String t1 = "9" + digits(7);
    final String t2 = "9" + digits(7);

    Map<String, Object> patent(String number, String title, String type, String filing, String grant, List<String> cpc,
                               String assignee, int claims) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("patentNumber", number);
        p.put("applicationNumber", "17/" + digits(3) + "," + digits(3));
        p.put("title", title);
        p.put("abstract", "An apparatus relating to " + word + " technology with improved efficiency.");
        p.put("type", type);
        p.put("filingDate", filing);
        p.put("grantDate", grant);
        p.put("cpcCodes", cpc);
        p.put("claims", java.util.stream.IntStream.rangeClosed(1, claims)
                .mapToObj(n -> n == 1
                        ? Map.<String, Object>of("number", 1, "text", "A device comprising a widget.")
                        : Map.<String, Object>of("number", n, "text", "The device of claim 1, wherein x.", "dependsOn", 1))
                .toList());
        p.put("inventors", List.of(Map.of("name", "Ivy Inventor", "city", "Austin", "state", "TX", "country", "US")));
        p.put("assignees", assignee == null ? List.of() : List.of(Map.of("name", assignee, "country", "US")));
        p.put("ingestJobId", "it-job");
        p.put("source", "INGEST");
        return p;
    }

    Map<String, Object> trademark(String serial, String mark, int niceClass, String owner) {
        return Map.of(
                "serialNumber", serial,
                "markText", mark,
                "filingDate", "2022-03-01",
                "registrationDate", "2023-01-10",
                "owner", owner,
                "filingBasis", "1A",
                "goodsAndServices", List.of(Map.of("niceClass", niceClass, "description", "Goods about " + word)),
                "events", List.of(
                        Map.of("date", "2023-01-10", "code", "R.PR", "description", "REGISTERED-PRINCIPAL REGISTER"),
                        Map.of("date", "2022-03-03", "code", "NWAP", "description", "NEW APPLICATION ENTERED")));
    }

    @BeforeAll
    void insertFixtures() throws Exception {
        String assignee = "Zorblax " + assigneeToken + " Industries";
        List<Map<String, Object>> patents = List.of(
                patent(p1, "Solar " + word + " collector", "UTILITY", "2019-04-01", "2021-06-01", List.of("Y02E 10/40", "H01L31/05"), assignee, 3),
                patent(p2, word + " " + word + " storage array", "UTILITY", "2020-05-01", "2022-07-05", List.of("Y02E70/30"), assignee, 12),
                patent(p3, "Ornamental " + word + " lamp", "DESIGN", "2021-01-04", null, List.of(), null, 1));
        mvc.perform(internal("/internal/v1/patents/bulk-upsert", patents))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(3))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.failed.length()").value(0));
        mvc.perform(internal("/internal/v1/trademarks/bulk-upsert", List.of(
                        trademark(t1, word.toUpperCase() + " ROASTERS", 30, "Heron " + assigneeToken + " Coffee LLC"),
                        trademark(t2, word.toUpperCase() + " LABS", 9, "Other Owner Inc."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(2));
    }

    @AfterAll
    void deleteFixtures() {
        jdbc.update("DELETE FROM patents WHERE patent_number IN (?, ?, ?)", p1, p2, p3);
        jdbc.update("DELETE FROM trademarks WHERE serial_number IN (?, ?)", t1, t2);
    }

    @Test
    void fullTextSearch_matchesTitleAbstract_andRanksByRelevance() throws Exception {
        mvc.perform(get("/api/v1/patents").param("q", word))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                // The title repeating the term twice ranks first.
                .andExpect(jsonPath("$.content[0].patentNumber").value(p2))
                .andExpect(jsonPath("$.content[0].assignees[0]").value(containsString(assigneeToken)))
                .andExpect(jsonPath("$.content[0].inventors[0]").value("Ivy Inventor"))
                .andExpect(jsonPath("$.content[0].source").value("INGEST"));
        // websearch syntax: exclusion.
        mvc.perform(get("/api/v1/patents").param("q", word + " -solar"))
                .andExpect(jsonPath("$.totalElements").value(2));
        // Assignee text is part of the search vector.
        mvc.perform(get("/api/v1/patents").param("q", assigneeToken))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filters_typeCpcAssigneeDates_andPaging() throws Exception {
        mvc.perform(get("/api/v1/patents").param("q", word).param("type", "DESIGN"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].patentNumber").value(p3))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        mvc.perform(get("/api/v1/patents").param("q", word).param("cpc", "y02e"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/patents").param("q", word).param("cpc", "Y02E10"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/patents").param("assignee", assigneeToken.toUpperCase()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/patents").param("q", word).param("filedFrom", "2020-01-01").param("grantedTo", "2022-12-31"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].patentNumber").value(p2));
        mvc.perform(get("/api/v1/patents").param("q", word).param("status", "GRANTED").param("inventor", "ivy"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/patents").param("q", word).param("sort", "filingDate,asc").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].patentNumber").value(p3));
        mvc.perform(get("/api/v1/patents").param("q", word).param("sort", "grantDate,desc"))
                .andExpect(jsonPath("$.content[0].patentNumber").value(p2))
                .andExpect(jsonPath("$.content[2].patentNumber").value(p3));
    }

    @Test
    void searchPage_loadsChildCollectionsWithoutNPlusOne() throws Exception {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        mvc.perform(get("/api/v1/patents").param("q", word)).andExpect(status().isOk());

        // count + page + one batch each for assignees and inventors.
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void facets_detail_stats() throws Exception {
        mvc.perform(get("/api/v1/patents/facets").param("q", word))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types[0].value").value("UTILITY"))
                .andExpect(jsonPath("$.types[0].count").value(2))
                .andExpect(jsonPath("$.cpcSections[0].value").value("Y"))
                .andExpect(jsonPath("$.years.length()").value(3))
                .andExpect(jsonPath("$.topAssignees[0].count").value(2));

        mvc.perform(get("/api/v1/patents/{n}", p1.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patentNumber").value(p1))
                .andExpect(jsonPath("$.primaryCpc").value("Y02E10/40"))
                .andExpect(jsonPath("$.cpcCodes.length()").value(2))
                .andExpect(jsonPath("$.claims.length()").value(3))
                .andExpect(jsonPath("$.claims[1].dependsOn").value(1))
                .andExpect(jsonPath("$.claims[1].independent").value(false))
                .andExpect(jsonPath("$.expirationDate").value("2039-04-01"))
                .andExpect(jsonPath("$.ingestJobId").value("it-job"));

        mvc.perform(get("/api/v1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastIngestAt").isNotEmpty());
    }

    @Test
    void bulkUpsert_sameKeyAgain_updatesAndReplacesChildren() throws Exception {
        String newWord = uniqueWord();
        Map<String, Object> updated = patent(p2, word + " " + word + " storage array " + newWord, "UTILITY", "2020-05-01", "2022-07-05",
                List.of("Y02E70/30"), "Zorblax " + assigneeToken + " Industries", 2);
        Map<String, Object> invalid = patent("USIT" + digits(8) + "B2", "bad", "UTILITY", "2020-05-01", "2019-01-01", List.of(), null, 1);

        mvc.perform(internal("/internal/v1/patents/bulk-upsert", List.of(updated, invalid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.failed[0].error").value("grantDate must not be before filingDate"));

        mvc.perform(get("/api/v1/patents/{n}", p2))
                .andExpect(jsonPath("$.claims.length()").value(2))
                .andExpect(jsonPath("$.title").value(containsString(newWord)));
        mvc.perform(get("/api/v1/patents").param("q", newWord)).andExpect(jsonPath("$.totalElements").value(1));
        Long version = jdbc.queryForObject("SELECT version FROM patents WHERE patent_number = ?", Long.class, p2);
        assertThat(version).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void trademarks_searchFiltersAndTsdrDetail() throws Exception {
        mvc.perform(get("/api/v1/trademarks").param("q", word))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/trademarks").param("q", word).param("niceClass", "30"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].serialNumber").value(t1))
                .andExpect(jsonPath("$.content[0].niceClasses[0]").value(30));
        mvc.perform(get("/api/v1/trademarks").param("owner", assigneeToken).param("status", "LIVE_REGISTERED"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/trademarks/{s}", t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE_REGISTERED"))
                .andExpect(jsonPath("$.events[0].code").value("NWAP"))
                .andExpect(jsonPath("$.statusDate").value("2023-01-10"))
                .andExpect(jsonPath("$.goodsAndServices[0].niceClass").value(30));
        mvc.perform(get("/api/v1/trademarks/{s}", "12345678")).andExpect(status().isNotFound());
    }

    @Test
    void exports_streamCsvAndJson_honoringFilters() throws Exception {
        MvcResult async = mvc.perform(get("/api/v1/datasets/patents.csv").param("q", word).param("sort", "patentNumber,asc"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String csv = mvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("patents.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString();
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(4);
        assertThat(lines[0]).startsWith("patentNumber,applicationNumber,title");
        assertThat(csv).contains(p1).contains(p2).contains(p3);

        MvcResult asyncJson = mvc.perform(get("/api/v1/datasets/trademarks.json").param("q", word).param("limit", "1"))
                .andExpect(request().asyncStarted())
                .andReturn();
        JsonNode array = json.readTree(mvc.perform(asyncDispatch(asyncJson)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(array.size()).isEqualTo(1);

        mvc.perform(get("/api/v1/datasets/patents.json").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limit must be between 1 and 10000"));
    }
}
