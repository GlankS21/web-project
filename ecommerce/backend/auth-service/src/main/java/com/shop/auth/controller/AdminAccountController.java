package com.shop.auth.controller;

import com.shop.auth.config.OpenApiConfig;
import com.shop.auth.dto.request.UpdateAccountStatusRequest;
import com.shop.auth.dto.request.UpdateRolesRequest;
import com.shop.auth.dto.response.AccountResponse;
import com.shop.auth.service.AccountAdminService;
import com.shop.common.dto.ApiResponse;
import com.shop.common.dto.PageResponse;
import com.shop.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Account administration", description = "Roles and status of accounts (admin only)")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/auth/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountAdminService accountAdminService;

    @Operation(summary = "Search accounts by email")
    @GetMapping
    public ApiResponse<PageResponse<AccountResponse>> search(
            @Parameter(description = "Part of the email address")
            @RequestParam(name = "email", required = false) String email,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(accountAdminService.searchAccounts(email, pageable));
    }

    @Operation(summary = "Get an account")
    @GetMapping("/{accountId}")
    public ApiResponse<AccountResponse> get(@PathVariable("accountId") UUID accountId) {
        return ApiResponse.success(accountAdminService.getAccount(accountId));
    }

    @Operation(summary = "Replace the roles of an account")
    @PutMapping("/{accountId}/roles")
    public ApiResponse<AccountResponse> updateRoles(@PathVariable("accountId") UUID accountId,
                                                    @Valid @RequestBody UpdateRolesRequest request) {
        return ApiResponse.success("Roles updated",
                accountAdminService.updateRoles(SecurityUtils.currentUserId(), accountId, request));
    }

    @Operation(summary = "Enable or disable an account (disabling revokes its sessions)")
    @PatchMapping("/{accountId}/status")
    public ApiResponse<AccountResponse> updateStatus(@PathVariable("accountId") UUID accountId,
                                                     @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ApiResponse.success("Status updated",
                accountAdminService.updateStatus(SecurityUtils.currentUserId(), accountId, request));
    }
}
