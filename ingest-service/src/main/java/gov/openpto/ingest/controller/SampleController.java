package gov.openpto.ingest.controller;

import gov.openpto.ingest.dto.SampleResponse;
import gov.openpto.ingest.service.SampleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingest/samples")
@Tag(name = "Samples")
@SecurityRequirements
public class SampleController {

    private final SampleService sampleService;

    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @Operation(summary = "List sample USPTO files (public)")
    @GetMapping
    public List<SampleResponse> list() {
        return sampleService.list();
    }

    @Operation(summary = "Download a sample file (public)")
    @GetMapping(path = "/{name}", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<Resource> download(@PathVariable String name) {
        Resource resource = sampleService.get(name);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(resource);
    }
}