package gov.openpto.fee.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.openpto.fee.config.SecurityConfig;
import gov.openpto.fee.domain.EntitySize;
import gov.openpto.fee.domain.FeeCategory;
import gov.openpto.fee.domain.FeeRuleViolationException;
import gov.openpto.fee.domain.FeeUnit;
import gov.openpto.fee.domain.MaintenanceStatus;
import gov.openpto.fee.domain.RuleViolation;
import gov.openpto.fee.dto.FeeItemResponse;
import gov.openpto.fee.dto.FeeQuoteResponse;
import gov.openpto.fee.dto.LineItemResponse;
import gov.openpto.fee.dto.MaintenanceResponse;
import gov.openpto.fee.dto.MaintenanceWindowResponse;
import gov.openpto.fee.dto.QuoteKind;
import gov.openpto.fee.dto.SavedQuoteResponse;
import gov.openpto.fee.dto.ScheduleDetailResponse;
import gov.openpto.fee.dto.ScheduleSummaryResponse;
import gov.openpto.fee.dto.SubtotalResponse;
import gov.openpto.fee.exception.NotFoundException;
import gov.openpto.fee.service.FeeCalculationService;
import gov.openpto.fee.service.FeeScheduleService;
import gov.openpto.fee.service.QuoteService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {FeeScheduleController.class, FeeCalculationController.class, QuoteController.class})
@Import(SecurityConfig.class)
class FeeControllersWebMvcTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private FeeScheduleService scheduleService;
    @MockitoBean
    private FeeCalculationService calculationService;
    @MockitoBean
    private QuoteService quoteService;

    private static FeeQuoteResponse sampleQuote() {
        return new FeeQuoteResponse("FY2025", "FY2025 name", EntitySize.SMALL,
                List.of(new LineItemResponse("CLAIM_OVER_20", "Each claim in excess of 20", 5,
                        new BigDecimal("80.00"), new BigDecimal("400.00"))),
                List.of(new SubtotalResponse("Excess claims", new BigDecimal("400.00"))),
                new BigDecimal("1680.00"), "USD", List.of("note"), List.of());
    }

    @Nested
    class Schedules {

        @Test
        void list_returns200() throws Exception {
            when(scheduleService.list()).thenReturn(List.of(
                    new ScheduleSummaryResponse(1L, "FY2025", "n", LocalDate.of(2025, 1, 19), null, true)));
            mvc.perform(get("/api/v1/fees/schedules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].code").value("FY2025"))
                    .andExpect(jsonPath("$[0].effectiveFrom").value("2025-01-19"))
                    .andExpect(jsonPath("$[0].current").value(true));
        }

        @Test
        void get_withCategory_returns200() throws Exception {
            when(scheduleService.get("current", FeeCategory.PATENT)).thenReturn(new ScheduleDetailResponse(1L, "FY2025", "n",
                    LocalDate.of(2025, 1, 19), null, true, List.of(new FeeItemResponse("UTIL_FILING", "d",
                    FeeCategory.PATENT, "Filing", new BigDecimal("350.00"), new BigDecimal("140.00"),
                    new BigDecimal("70.00"), FeeUnit.EACH))));
            mvc.perform(get("/api/v1/fees/schedules/current").param("category", "PATENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].largeEntity").value(350.00))
                    .andExpect(jsonPath("$.items[0].unit").value("EACH"));
        }

        @Test
        void get_unknown_returns404ProblemWithRequestId() throws Exception {
            when(scheduleService.get(eq("FY1999"), isNull())).thenThrow(new NotFoundException("Fee schedule 'FY1999' not found"));
            mvc.perform(get("/api/v1/fees/schedules/FY1999").header("X-Request-Id", "req-123"))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON))
                    .andExpect(header().string("X-Request-Id", "req-123"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.detail").value("Fee schedule 'FY1999' not found"))
                    .andExpect(jsonPath("$.instance").value("/api/v1/fees/schedules/FY1999"))
                    .andExpect(jsonPath("$.requestId").value("req-123"))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        void get_badCategory_returns400() throws Exception {
            mvc.perform(get("/api/v1/fees/schedules/current").param("category", "SPACE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.requestId").exists());
        }
    }

    @Nested
    class Calculators {

        @Test
        void patentFiling_returns200WithMoneyScale2() throws Exception {
            when(calculationService.patentFiling(any())).thenReturn(sampleQuote());
            mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON).content("""
                            {"applicationType":"UTILITY","entitySize":"SMALL","totalClaims":25,"independentClaims":5}"""))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")))
                    .andExpect(jsonPath("$.total").value(1680.00))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.lineItems[0].feeCode").value("CLAIM_OVER_20"))
                    .andExpect(jsonPath("$.subtotals[0].group").value("Excess claims"));
        }

        @Test
        void patentFiling_moneySerializedWithTwoDecimals() throws Exception {
            when(calculationService.patentFiling(any())).thenReturn(sampleQuote());
            String body = mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"applicationType\":\"UTILITY\",\"entitySize\":\"SMALL\"}"))
                    .andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(body).contains("\"total\":1680.00").contains("\"unitAmount\":80.00");
        }

        @Test
        void patentFiling_validationErrors_return400WithFieldErrors() throws Exception {
            mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON).content("""
                            {"applicationType":"UTILITY","totalClaims":501,"extensionMonths":6}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.requestId").exists())
                    .andExpect(jsonPath("$.errors", hasSize(3)))
                    .andExpect(jsonPath("$.errors[?(@.field == 'entitySize')].message").exists())
                    .andExpect(jsonPath("$.errors[?(@.field == 'totalClaims')].message").exists())
                    .andExpect(jsonPath("$.errors[?(@.field == 'extensionMonths')].message").exists());
            verifyNoInteractions(calculationService);
        }

        @Test
        void patentFiling_ruleViolation_returns400WithFieldErrors() throws Exception {
            when(calculationService.patentFiling(any())).thenThrow(new FeeRuleViolationException(
                    List.of(new RuleViolation("independentClaims", "must not exceed totalClaims"))));
            mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON).content("""
                            {"applicationType":"UTILITY","entitySize":"LARGE","totalClaims":2,"independentClaims":3}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Fee rule violation"))
                    .andExpect(jsonPath("$.errors[0].field").value("independentClaims"))
                    .andExpect(jsonPath("$.errors[0].message").value("must not exceed totalClaims"));
        }

        @Test
        void patentFiling_malformedJson_returns400Problem() throws Exception {
            mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON).content("{\"applicationType\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON))
                    .andExpect(jsonPath("$.requestId").exists());
        }

        @Test
        void patentFiling_unknownEnum_returns400() throws Exception {
            mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"applicationType\":\"TIME_MACHINE\",\"entitySize\":\"LARGE\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void unexpectedError_returns500WithoutStackTrace() throws Exception {
            when(calculationService.patentFiling(any())).thenThrow(new IllegalStateException("db exploded: secret"));
            mvc.perform(post("/api/v1/fees/patent/filing").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"applicationType\":\"UTILITY\",\"entitySize\":\"LARGE\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value(matchesPattern("^An unexpected error occurred.*")))
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(jsonPath("$.requestId").exists());
        }

        @Test
        void maintenance_returns200() throws Exception {
            when(calculationService.maintenance(any())).thenReturn(new MaintenanceResponse(
                    List.of(new MaintenanceWindowResponse("3.5", LocalDate.of(2024, 6, 15), LocalDate.of(2024, 12, 15),
                            LocalDate.of(2025, 6, 15), MaintenanceStatus.GRACE_PERIOD, new BigDecimal("860.00"),
                            new BigDecimal("216.00"), new BigDecimal("1076.00"))),
                    false, "FY2025", "n", EntitySize.SMALL, LocalDate.of(2021, 6, 15), LocalDate.of(2025, 2, 1), sampleQuote()));
            mvc.perform(post("/api/v1/fees/patent/maintenance").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"entitySize\":\"SMALL\",\"grantDate\":\"2021-06-15\",\"asOfDate\":\"2025-02-01\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.windows[0].stage").value("3.5"))
                    .andExpect(jsonPath("$.windows[0].status").value("GRACE_PERIOD"))
                    .andExpect(jsonPath("$.windows[0].graceEnds").value("2025-06-15"))
                    .andExpect(jsonPath("$.patentExpired").value(false))
                    .andExpect(jsonPath("$.scheduleCode").value("FY2025"));
        }

        @Test
        void maintenance_missingGrantDate_andBadStage_return400() throws Exception {
            mvc.perform(post("/api/v1/fees/patent/maintenance").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"entitySize\":\"SMALL\",\"paidStages\":[\"4.5\"]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field == 'grantDate')]").exists())
                    .andExpect(jsonPath("$.errors[?(@.field == 'paidStages[0]')]").exists());
        }

        @Test
        void trademark_returns200_andValidatesClasses() throws Exception {
            when(calculationService.trademark(any())).thenReturn(new FeeQuoteResponse("FY2025", "n", null, List.of(),
                    List.of(), new BigDecimal("1050.00"), "USD", List.of(), List.of()));
            mvc.perform(post("/api/v1/fees/trademark").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"filingType\":\"APPLICATION\",\"numberOfClasses\":3}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1050.00))
                    .andExpect(jsonPath("$.entitySize").value(org.hamcrest.Matchers.nullValue()));
            mvc.perform(post("/api/v1/fees/trademark").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"filingType\":\"APPLICATION\",\"numberOfClasses\":46}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("numberOfClasses"));
        }
    }

    @Nested
    class Quotes {

        private SavedQuoteResponse saved(UUID id) {
            FeeQuoteResponse q = sampleQuote();
            return new SavedQuoteResponse(id, QuoteKind.PATENT_FILING, "label", null, q.scheduleCode(), q.scheduleName(),
                    q.entitySize(), q.lineItems(), q.subtotals(), q.total(), q.currency(), q.notes(), q.warnings(), null,
                    Instant.parse("2025-06-01T15:00:00Z"));
        }

        @Test
        void create_returns201WithLocation() throws Exception {
            UUID id = UUID.fromString("3f1c2a8e-7c1b-4d5e-9a0b-2b7c1f0d9e11");
            when(quoteService.create(any())).thenReturn(saved(id));
            mvc.perform(post("/api/v1/fees/quotes").contentType(MediaType.APPLICATION_JSON).content("""
                            {"kind":"PATENT_FILING","label":"label","request":{"applicationType":"UTILITY","entitySize":"SMALL"}}"""))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/fees/quotes/" + id))
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.total").value(1680.00))
                    .andExpect(jsonPath("$.createdAt").value("2025-06-01T15:00:00Z"));
        }

        @Test
        void create_missingKindAndRequest_returns400() throws Exception {
            mvc.perform(post("/api/v1/fees/quotes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasSize(2)));
        }

        @Test
        void get_returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(quoteService.get(id)).thenReturn(saved(id));
            mvc.perform(get("/api/v1/fees/quotes/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kind").value("PATENT_FILING"));
        }

        @Test
        void get_unknown_returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(quoteService.get(id)).thenThrow(new NotFoundException("Quote " + id + " not found"));
            mvc.perform(get("/api/v1/fees/quotes/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Quote " + id + " not found"));
        }

        @Test
        void get_invalidUuid_returns400() throws Exception {
            mvc.perform(get("/api/v1/fees/quotes/not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", PROBLEM_JSON));
        }
    }

    @Test
    void unlistedPath_isDenied() throws Exception {
        mvc.perform(get("/internal/v1/anything")).andExpect(status().isForbidden());
    }

    @Test
    void unsafeRequestIdHeader_isReplaced() throws Exception {
        when(scheduleService.list()).thenReturn(List.of());
        mvc.perform(get("/api/v1/fees/schedules").header("X-Request-Id", "bad id\r\nX-Evil: 1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));
    }
}
