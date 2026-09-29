package gov.openpto.fee.controller;

import gov.openpto.fee.config.OpenApiConfig;
import gov.openpto.fee.dto.FeeQuoteResponse;
import gov.openpto.fee.dto.MaintenanceRequest;
import gov.openpto.fee.dto.MaintenanceResponse;
import gov.openpto.fee.dto.PatentFilingRequest;
import gov.openpto.fee.dto.TrademarkFeeRequest;
import gov.openpto.fee.service.FeeCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_CALCULATORS)
public class FeeCalculationController {

    private final FeeCalculationService service;

    @PostMapping("/patent/filing")
    @Operation(summary = "Patent filing fees", description = "Itemized filing, search, examination, excess-claim, "
            + "size, surcharge, extension, RCE and Track One fees from the schedule effective on filingDate.")
    @ApiResponse(responseCode = "200", description = "Quote")
    @ApiResponse(responseCode = "400", description = "Validation or fee-rule violation (ProblemDetail with errors)")
    @ApiResponse(responseCode = "404", description = "No schedule effective on filingDate")
    public FeeQuoteResponse patentFiling(@Valid @RequestBody PatentFilingRequest request) {
        return service.patentFiling(request);
    }

    @PostMapping("/patent/maintenance")
    @Operation(summary = "Patent maintenance-fee windows",
            description = "3.5/7.5/11.5-year windows with status and amount payable on asOfDate.")
    @ApiResponse(responseCode = "200", description = "Windows")
    @ApiResponse(responseCode = "400", description = "Validation failure")
    public MaintenanceResponse maintenance(@Valid @RequestBody MaintenanceRequest request) {
        return service.maintenance(request);
    }

    @PostMapping("/trademark")
    @Operation(summary = "Trademark fees", description = "Per-class trademark fees from the schedule effective on filingDate.")
    @ApiResponse(responseCode = "200", description = "Quote")
    @ApiResponse(responseCode = "400", description = "Validation failure")
    public FeeQuoteResponse trademark(@Valid @RequestBody TrademarkFeeRequest request) {
        return service.trademark(request);
    }
}
