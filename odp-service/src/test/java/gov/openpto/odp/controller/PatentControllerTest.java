package gov.openpto.odp.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.openpto.odp.dto.PageResponse;
import gov.openpto.odp.dto.PatentFacets;
import gov.openpto.odp.dto.PatentFilter;
import gov.openpto.odp.dto.PatentSummary;
import gov.openpto.odp.exception.NotFoundException;
import gov.openpto.odp.model.PatentStatus;
import gov.openpto.odp.model.PatentType;
import gov.openpto.odp.model.RecordSource;
import gov.openpto.odp.service.PatentService;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PatentController.class)
@Import(WebSliceConfig.class)
class PatentControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PatentService patents;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void search_validParams_returnsContractPageShape() throws Exception {
        PatentSummary hit = new PatentSummary("US11234567B2", "17/123,456", "Battery cell", PatentType.UTILITY,
                PatentStatus.GRANTED, LocalDate.of(2020, 3, 14), LocalDate.of(2022, 2, 1), "H01M10/0562",
                List.of("Acme Inc."), List.of("Ada Lovelace"), "A battery...", RecordSource.SEED);
        given(patents.search(any(), any())).willReturn(PageResponse.of(List.of(hit), 0, 20, 1));

        mvc.perform(get("/api/v1/patents").param("q", "battery").param("type", "UTILITY").param("filedFrom", "2020-01-01"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.content[0].patentNumber").value("US11234567B2"))
                .andExpect(jsonPath("$.content[0].filingDate").value("2020-03-14"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void search_sizeOver100_returns400WithFieldErrors() throws Exception {
        mvc.perform(get("/api/v1/patents").param("size", "500").header("X-Request-Id", "req-123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/v1/patents"))
                .andExpect(jsonPath("$.requestId").value("req-123"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("size")));
        verifyNoInteractions(patents);
    }

    @Test
    void search_unknownEnum_returns400WithInvalidValueMessage() throws Exception {
        mvc.perform(get("/api/v1/patents").param("status", "ALIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("invalid value 'ALIVE'"));
    }

    @Test
    void search_malformedDate_returns400() throws Exception {
        mvc.perform(get("/api/v1/patents").param("filedFrom", "01/02/2020"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("filedFrom"));
    }

    @Test
    void detail_unknownNumber_returns404Problem() throws Exception {
        given(patents.detail("US1B2")).willThrow(NotFoundException.of("Patent", "US1B2"));

        mvc.perform(get("/api/v1/patents/US1B2"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Patent 'US1B2' was not found"))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }

    @Test
    void detail_invalidCharacters_returns400() throws Exception {
        mvc.perform(get("/api/v1/patents/US1;drop"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void facets_passesFilter() throws Exception {
        given(patents.facets(eq(new PatentFilter(null, PatentType.DESIGN, null, null, null, null, null, null, null, null))))
                .willReturn(new PatentFacets(List.of(), List.of(), List.of(), List.of(), List.of()));

        mvc.perform(get("/api/v1/patents/facets").param("type", "DESIGN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types").isArray())
                .andExpect(jsonPath("$.topAssignees").isArray());
    }
}
