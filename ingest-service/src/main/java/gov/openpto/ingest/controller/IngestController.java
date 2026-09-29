package gov.openpto.ingest.controller;

import gov.openpto.ingest.config.OpenApiConfig;
import gov.openpto.ingest.dto.IngestJobResponse;
import gov.openpto.ingest.dto.PageResponse;
import gov.openpto.ingest.dto.StatsResponse;
import gov.openpto.ingest.model.JobStatus;
import gov.openpto.ingest.service.IngestJobService;
import gov.openpto.ingest.service.JsonDownload;
import gov.openpto.ingest.service.UploadService;
import gov.openpto.ingest.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/ingest")
@Tag(name = "Ingest")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class IngestController {

    private final UploadService uploadService;
    private final IngestJobService jobService;

    public IngestController(UploadService uploadService, IngestJobService jobService) {
        this.uploadService = uploadService;
        this.jobService = jobService;
    }

    @Operation(summary = "Upload USPTO bulk XML",
            description = "1-10 files, each .xml (one or many concatenated documents) or .zip of .xml, max 50 MB each. "
                    + "Each XML file becomes a job; processing is asynchronous (poll the job).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(type = "object", requiredProperties = {"files"}),
                    encoding = @Encoding(name = "files", contentType = "application/xml, application/zip"))))
    @ApiResponse(responseCode = "202", description = "Jobs queued")
    @ApiResponse(responseCode = "400", description = "Invalid file type/content/count or unsafe archive")
    @ApiResponse(responseCode = "413", description = "File larger than 50 MB")
    @PostMapping(path = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<IngestJobResponse> upload(
            @Parameter(description = "XML or ZIP files", content = @Content(array = @ArraySchema(
                    schema = @Schema(type = "string", format = "binary"))))
            @RequestPart("files") List<MultipartFile> files,
            JwtAuthenticationToken auth) {
        return uploadService.upload(files, CurrentUser.from(auth));
    }

    @Operation(summary = "List ingest jobs", description = "Own jobs; ADMIN sees every job. Sort: createdAt, fileName, "
            + "status, sizeBytes, finishedAt, recordsTotal.")
    @GetMapping("/jobs")
    public PageResponse<IngestJobResponse> list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(example = "createdAt,desc") @RequestParam(required = false) String sort,
            JwtAuthenticationToken auth) {
        return jobService.list(CurrentUser.from(auth), status, page, size, sort);
    }

    @Operation(summary = "Job detail with stages and the first 200 record errors")
    @ApiResponse(responseCode = "403", description = "Job belongs to another user")
    @ApiResponse(responseCode = "404", description = "Unknown job")
    @GetMapping("/jobs/{id}")
    public IngestJobResponse get(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return jobService.get(id, CurrentUser.from(auth));
    }

    @Operation(summary = "Download the transformed JSON (array of PatentUpsert / TrademarkUpsert records)")
    @GetMapping(path = "/jobs/{id}/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> json(@PathVariable UUID id, JwtAuthenticationToken auth) {
        JsonDownload download = jobService.jsonDownload(id, CurrentUser.from(auth));
        StreamingResponseBody body = out -> download.writer().writeTo(out);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName()).build().toString())
                .body(body);
    }

    @Operation(summary = "Retry a FAILED or PARTIAL job from its raw object")
    @ApiResponse(responseCode = "202", description = "Job re-queued")
    @ApiResponse(responseCode = "409", description = "Job is not FAILED or PARTIAL")
    @PostMapping("/jobs/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IngestJobResponse retry(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return jobService.retry(id, CurrentUser.from(auth));
    }

    @Operation(summary = "Delete a job and its raw/processed objects (owner or ADMIN)")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, JwtAuthenticationToken auth) {
        jobService.delete(id, CurrentUser.from(auth));
    }

    @Operation(summary = "Ingest statistics (own jobs; ADMIN: all)")
    @GetMapping("/stats")
    public StatsResponse stats(JwtAuthenticationToken auth) {
        return jobService.stats(CurrentUser.from(auth));
    }
}