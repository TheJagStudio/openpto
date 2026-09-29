package gov.openpto.fee.controller;

import gov.openpto.fee.config.OpenApiConfig;
import gov.openpto.fee.dto.CreateQuoteRequest;
import gov.openpto.fee.dto.SavedQuoteResponse;
import gov.openpto.fee.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/quotes")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_QUOTES)
public class QuoteController {

    private final QuoteService service;

    @PostMapping
    @Operation(summary = "Save a quote", description = "Recomputes the quote from `request`; returns it with an id to share.")
    @ApiResponse(responseCode = "201", description = "Saved")
    @ApiResponse(responseCode = "400", description = "Invalid kind/request")
    public ResponseEntity<SavedQuoteResponse> create(@Valid @RequestBody CreateQuoteRequest request) {
        SavedQuoteResponse saved = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/fees/quotes/" + saved.id())).body(saved);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a saved quote (recomputed from the stored request)")
    @ApiResponse(responseCode = "200", description = "Quote")
    @ApiResponse(responseCode = "404", description = "Unknown quote id")
    public SavedQuoteResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
