package gov.openpto.odp.controller;

import gov.openpto.odp.config.OpenApiConfig;
import gov.openpto.odp.dto.BulkUpsertResponse;
import gov.openpto.odp.dto.PatentUpsert;
import gov.openpto.odp.dto.TrademarkUpsert;
import gov.openpto.odp.dto.UsageReportRequest;
import gov.openpto.odp.dto.VerifyKeyRequest;
import gov.openpto.odp.dto.VerifyKeyResponse;
import gov.openpto.odp.service.ApiKeyService;
import gov.openpto.odp.service.BulkUpsertService;
import gov.openpto.odp.service.UsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Service-to-service endpoints; guarded by X-Internal-Token and never routed by the gateway. */
@RestController
@RequestMapping("/internal/v1")
@Tag(name = "Internal")
@SecurityRequirement(name = OpenApiConfig.INTERNAL)
@RequiredArgsConstructor
public class InternalController {

    private final ApiKeyService apiKeys;
    private final UsageService usage;
    private final BulkUpsertService bulkUpsert;

    @PostMapping("/api-keys/verify")
    @Operation(summary = "Verify an API key (gateway)", description = "valid=false for unknown, malformed or revoked keys.")
    public VerifyKeyResponse verify(@Valid @RequestBody VerifyKeyRequest request) {
        return apiKeys.verify(request.key());
    }

    @PostMapping("/usage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Add batched per-key daily usage counts (gateway)")
    public void usage(@Valid @RequestBody UsageReportRequest request) {
        usage.record(request);
    }

    @PostMapping("/patents/bulk-upsert")
    @Operation(summary = "Insert or update up to 1000 patents keyed by patentNumber (ingest)")
    public BulkUpsertResponse upsertPatents(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = PatentUpsert.class))))
            @RequestBody @Size(max = BulkUpsertService.MAX_BATCH) List<JsonNode> records) {
        return bulkUpsert.upsertPatents(records);
    }

    @PostMapping("/trademarks/bulk-upsert")
    @Operation(summary = "Insert or update up to 1000 trademarks keyed by serialNumber (ingest)")
    public BulkUpsertResponse upsertTrademarks(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = TrademarkUpsert.class))))
            @RequestBody @Size(max = BulkUpsertService.MAX_BATCH) List<JsonNode> records) {
        return bulkUpsert.upsertTrademarks(records);
    }
}
