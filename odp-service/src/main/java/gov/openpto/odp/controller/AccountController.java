package gov.openpto.odp.controller;

import gov.openpto.odp.config.OpenApiConfig;
import gov.openpto.odp.dto.ApiKeyResponse;
import gov.openpto.odp.dto.CreateApiKeyRequest;
import gov.openpto.odp.dto.UsageResponse;
import gov.openpto.odp.service.ApiKeyService;
import gov.openpto.odp.service.UsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@RequiredArgsConstructor
public class AccountController {

    private final ApiKeyService apiKeys;
    private final UsageService usage;

    @GetMapping("/api-keys")
    @Operation(summary = "List my API keys (active and revoked)")
    public List<ApiKeyResponse> listKeys(@AuthenticationPrincipal Jwt jwt) {
        return apiKeys.list(Principals.userId(jwt));
    }

    @PostMapping("/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an API key", description = "The plaintext `key` is returned only in this response.")
    @ApiResponse(responseCode = "409", description = "Already 5 active keys")
    public ApiKeyResponse createKey(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateApiKeyRequest request) {
        return apiKeys.create(Principals.userId(jwt), request);
    }

    @PostMapping("/api-keys/{id}/rotate")
    @Operation(summary = "Rotate an API key", description = "Issues a new secret (returned once); the old one stops working immediately.")
    @ApiResponse(responseCode = "404", description = "No such key for this user")
    @ApiResponse(responseCode = "409", description = "Key is revoked")
    public ApiKeyResponse rotateKey(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return apiKeys.rotate(Principals.userId(jwt), id);
    }

    @DeleteMapping("/api-keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke an API key")
    public void revokeKey(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        apiKeys.revoke(Principals.userId(jwt), id);
    }

    @GetMapping("/usage")
    @Operation(summary = "My API usage over the last N days (zero-filled)")
    public UsageResponse usage(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return usage.forUser(Principals.userId(jwt), days);
    }
}
