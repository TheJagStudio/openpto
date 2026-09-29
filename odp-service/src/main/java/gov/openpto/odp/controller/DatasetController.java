package gov.openpto.odp.controller;

import gov.openpto.odp.dto.PatentFilter;
import gov.openpto.odp.dto.TrademarkFilter;
import gov.openpto.odp.service.ExportService;
import gov.openpto.odp.service.ExportService.Format;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Streamed bulk exports; same filters as the search endpoints, at most 10,000 rows. */
@RestController
@RequestMapping("/api/v1/datasets")
@Tag(name = "Datasets")
@RequiredArgsConstructor
public class DatasetController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final String SORT_PATTERN = "^[A-Za-z]{1,40}(,(?i:asc|desc))?$";

    private final ExportService exports;

    @GetMapping("/patents.json")
    @Operation(summary = "Export patents as a JSON array (streamed)")
    public ResponseEntity<StreamingResponseBody> patentsJson(
            @Valid @ParameterObject PatentFilter filter,
            @Parameter(description = "field,asc|desc") @RequestParam(required = false) @Pattern(regexp = SORT_PATTERN) String sort,
            @Parameter(description = "Max rows (1-10000, default 10000)") @RequestParam(required = false) Integer limit) {
        return attachment("patents.json", MediaType.APPLICATION_JSON, exports.patents(filter, sort, limit, Format.JSON));
    }

    @GetMapping("/patents.csv")
    @Operation(summary = "Export patents as CSV (streamed)")
    public ResponseEntity<StreamingResponseBody> patentsCsv(
            @Valid @ParameterObject PatentFilter filter,
            @RequestParam(required = false) @Pattern(regexp = SORT_PATTERN) String sort,
            @RequestParam(required = false) Integer limit) {
        return attachment("patents.csv", CSV, exports.patents(filter, sort, limit, Format.CSV));
    }

    @GetMapping("/trademarks.json")
    @Operation(summary = "Export trademarks as a JSON array (streamed)")
    public ResponseEntity<StreamingResponseBody> trademarksJson(
            @Valid @ParameterObject TrademarkFilter filter,
            @RequestParam(required = false) @Pattern(regexp = SORT_PATTERN) String sort,
            @RequestParam(required = false) Integer limit) {
        return attachment("trademarks.json", MediaType.APPLICATION_JSON, exports.trademarks(filter, sort, limit, Format.JSON));
    }

    @GetMapping("/trademarks.csv")
    @Operation(summary = "Export trademarks as CSV (streamed)")
    public ResponseEntity<StreamingResponseBody> trademarksCsv(
            @Valid @ParameterObject TrademarkFilter filter,
            @RequestParam(required = false) @Pattern(regexp = SORT_PATTERN) String sort,
            @RequestParam(required = false) Integer limit) {
        return attachment("trademarks.csv", CSV, exports.trademarks(filter, sort, limit, Format.CSV));
    }

    private static ResponseEntity<StreamingResponseBody> attachment(String fileName, MediaType type, StreamingResponseBody body) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }
}
