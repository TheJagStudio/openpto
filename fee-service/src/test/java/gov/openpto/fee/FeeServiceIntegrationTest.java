package gov.openpto.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.openpto.fee.domain.FeeRate;
import gov.openpto.fee.domain.FeeSchedule;
import gov.openpto.fee.domain.TestSchedules;
import gov.openpto.fee.repository.SavedQuoteRepository;
import gov.openpto.fee.service.FeeScheduleCatalog;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full stack against Postgres {@code openpto_test}, schema {@code fees} (migrated by Flyway on startup).
 * Idempotent: it only reads seeded schedules and inserts new saved quotes with random UUIDs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeeServiceIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FeeScheduleCatalog catalog;
    @Autowired
    private SavedQuoteRepository quotes;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayMigratedIntoFeesSchema_andSeedMatchesTestFixtures() {
        assertThat(jdbc.queryForObject("select count(*) from fees.fee_schedule", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from fees.flyway_schema_history where success", Integer.class)).isGreaterThanOrEqualTo(2);

        List<FeeSchedule> fromDb = catalog.loadAll();
        assertThat(fromDb).extracting(FeeSchedule::code).containsExactly("FY2025", "FY2023");
        for (FeeSchedule expected : TestSchedules.all()) {
            FeeSchedule actual = fromDb.stream().filter(s -> s.code().equals(expected.code())).findFirst().orElseThrow();
            assertThat(actual.effectiveFrom()).isEqualTo(expected.effectiveFrom());
            assertThat(actual.effectiveTo()).isEqualTo(expected.effectiveTo());
            assertThat(actual.items()).hasSameSizeAs(expected.items());
            for (FeeRate rate : expected.items()) {
                FeeRate db = actual.require(rate.feeCode());
                assertThat(List.of(db.largeEntity(), db.smallEntity(), db.microEntity(), db.unit(), db.category(), db.group()))
                        .as(expected.code() + " " + rate.feeCode())
                        .isEqualTo(List.of(rate.largeEntity(), rate.smallEntity(), rate.microEntity(), rate.unit(),
                                rate.category(), rate.group()));
            }
        }
        assertThat(catalog.loadAll()).as("cached").isSameAs(fromDb);
    }

    @Test
    void schedules_listAndDetail() throws Exception {
        mvc.perform(get("/api/v1/fees/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("FY2025"))
                .andExpect(jsonPath("$[1].effectiveTo").value("2025-01-18"));
        mvc.perform(get("/api/v1/fees/schedules/FY2023").param("category", "TRADEMARK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(8));
        mvc.perform(get("/api/v1/fees/schedules/FY1900"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void patentFiling_25claims_5independent_small() throws Exception {
        mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationType":"UTILITY","entitySize":"SMALL","totalClaims":25,"independentClaims":5,
                         "filingDate":"2025-03-01"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleCode").value("FY2025"))
                .andExpect(jsonPath("$.total").value(1680.00))
                .andExpect(jsonPath("$.lineItems.length()").value(5));
    }

    @Test
    void scheduleSelection_byFilingDate() throws Exception {
        String body = "{\"applicationType\":\"UTILITY\",\"entitySize\":\"LARGE\",\"filingDate\":\"%s\"}";
        mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON).content(body.formatted("2025-01-18")))
                .andExpect(jsonPath("$.scheduleCode").value("FY2023"))
                .andExpect(jsonPath("$.total").value(1820.00));
        mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON).content(body.formatted("2025-01-19")))
                .andExpect(jsonPath("$.scheduleCode").value("FY2025"))
                .andExpect(jsonPath("$.total").value(2000.00));
        mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON).content(body.formatted("2020-01-01")))
                .andExpect(status().isNotFound());
    }

    @Test
    void trackOneLimits_return400() throws Exception {
        mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationType":"UTILITY","entitySize":"LARGE","totalClaims":31,"independentClaims":5,
                         "prioritizedExamination":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void maintenance_and_trademark() throws Exception {
        mvc.perform(post("/api/v1/fees/patent/maintenance").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entitySize\":\"SMALL\",\"grantDate\":\"2021-06-15\",\"asOfDate\":\"2025-02-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windows[0].status").value("GRACE_PERIOD"))
                .andExpect(jsonPath("$.windows[0].totalIfPaidOnAsOfDate").value(1076.00))
                .andExpect(jsonPath("$.payableNow.total").value(1076.00));
        mvc.perform(post("/api/v1/fees/trademark").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filingType\":\"APPLICATION\",\"numberOfClasses\":3,\"filingDate\":\"2025-03-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1050.00));
        mvc.perform(post("/api/v1/fees/trademark").contentType(MediaType.APPLICATION_JSON).content("""
                        {"filingType":"APPLICATION","numberOfClasses":2,"freeFormTextIds":true,"filingDate":"2024-03-01"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleCode").value("FY2023"))
                .andExpect(jsonPath("$.total").value(700.00))
                .andExpect(jsonPath("$.warnings.length()").value(1));
    }

    @Test
    void savedQuote_roundTrip_recomputedFromJsonb() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/fees/quotes").contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind":"PATENT_FILING","label":"it-test",
                         "request":{"applicationType":"UTILITY","entitySize":"MICRO","totalClaims":21,"filingDate":"2025-02-01"}}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.total").value(440.00))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        assertThat(quotes.findById(UUID.fromString(id))).isPresent();
        assertThat(jdbc.queryForObject("select request->>'totalClaims' from fees.saved_quote where id = ?::uuid",
                String.class, id)).isEqualTo("21");

        mvc.perform(get("/api/v1/fees/quotes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("it-test"))
                .andExpect(jsonPath("$.total").value(440.00))
                .andExpect(jsonPath("$.request.filingDate").value("2025-02-01"));

        mvc.perform(get("/api/v1/fees/quotes/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void savedMaintenanceQuote_includesWindows() throws Exception {
        mvc.perform(post("/api/v1/fees/quotes").contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind":"MAINTENANCE","request":{"entitySize":"LARGE","grantDate":"2021-06-15","asOfDate":"2024-07-01"}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleCode").value("FY2023"))
                .andExpect(jsonPath("$.total").value(2000.00))
                .andExpect(jsonPath("$.maintenance.windows[0].status").value("OPEN"));
    }

    @Test
    void operationalEndpoints() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("OpenPTO Fee Service"))
                .andExpect(jsonPath("$.paths['/api/v1/fees/patent/filing']").exists());
        mvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
    }
}
