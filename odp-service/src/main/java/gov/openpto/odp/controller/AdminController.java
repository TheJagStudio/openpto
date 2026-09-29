package gov.openpto.odp.controller;

import gov.openpto.odp.config.OpenApiConfig;
import gov.openpto.odp.dto.AdminUserResponse;
import gov.openpto.odp.dto.PageParams;
import gov.openpto.odp.dto.PageResponse;
import gov.openpto.odp.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUsers;

    @GetMapping("/users")
    @Operation(summary = "Users with API key counts (newest first)")
    public PageResponse<AdminUserResponse> users(
            @Parameter(description = "Email contains") @RequestParam(required = false) @Size(max = 254) String q,
            @Valid @ParameterObject PageParams paging) {
        return adminUsers.list(q, paging);
    }
}
