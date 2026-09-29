package gov.openpto.ingest.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.openpto.ingest.config.SecurityConfig;
import gov.openpto.ingest.service.SampleService;
import gov.openpto.ingest.web.ProblemResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SampleController.class)
@Import({SecurityConfig.class, ProblemResponseWriter.class, SampleService.class})
class SampleControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void list_isPublic() throws Exception {
        mvc.perform(get("/api/v1/ingest/samples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].name").value("ipg250107-sample.xml"))
                .andExpect(jsonPath("$[0].format").value("US_PATENT_GRANT"))
                .andExpect(jsonPath("$[0].sizeBytes").isNumber());
    }

    @Test
    void download_isPublicXmlAttachment() throws Exception {
        mvc.perform(get("/api/v1/ingest/samples/{name}", "apc240102-trademark-sample.xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"apc240102-trademark-sample.xml\""))
                .andExpect(content().string(startsWith("<?xml")));
    }

    @Test
    void download_unknownName_is404Problem() throws Exception {
        mvc.perform(get("/api/v1/ingest/samples/{name}", "secret.xml"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void nonIngestPath_isDenied() throws Exception {
        mvc.perform(get("/internal/v1/anything")).andExpect(status().isUnauthorized());
    }
}