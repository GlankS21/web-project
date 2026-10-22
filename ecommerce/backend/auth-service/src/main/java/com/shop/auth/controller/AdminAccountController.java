package com.shop.auth.controller;

import com.shop.auth.config.OpenApiConfig;
import com.shop.auth.dto.request.UpdateAccountStatusRequest;
import com.shop.auth.dto.request.UpdateRolesRequest;
import com.shop.auth.dto.response.AccountResponse;
import com.shop.auth.service.AccountAdminService;
import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
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
import org.springframework.context.MessageSource;
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

@Tag(name = "Администрирование аккаунтов", description = "Роли и статус аккаунтов (только для администратора)")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/auth/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    static final String ROLES_UPDATED = "auth.admin.roles-updated";
    static final String STATUS_UPDATED = "auth.admin.status-updated";

    private final AccountAdminService accountAdminService;
    private final MessageSource messageSource;

    @Operation(summary = "Найти аккаунты по e-mail")
    @GetMapping
    public ApiResponse<PageResponse<AccountResponse>> search(
            @Parameter(description = "Часть адреса e-mail", example = "ivan.petrov")
            @RequestParam(name = "email", required = false) String email,
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(accountAdminService.searchAccounts(email, pageable));
    }

    @Operation(summary = "Получить аккаунт")
    @GetMapping("/{accountId}")
    public ApiResponse<AccountResponse> get(
            @Parameter(description = "Идентификатор аккаунта") @PathVariable("accountId") UUID accountId) {
        return ApiResponse.success(accountAdminService.getAccount(accountId));
    }

    @Operation(summary = "Заменить роли аккаунта")
    @PutMapping("/{accountId}/roles")
    public ApiResponse<AccountResponse> updateRoles(
            @Parameter(description = "Идентификатор аккаунта") @PathVariable("accountId") UUID accountId,
            @Valid @RequestBody UpdateRolesRequest request) {
        return ApiResponse.success(message(ROLES_UPDATED),
                accountAdminService.updateRoles(SecurityUtils.currentUserId(), accountId, request));
    }

    @Operation(summary = "Разблокировать или заблокировать аккаунт (блокировка завершает его сеансы)")
    @PatchMapping("/{accountId}/status")
    public ApiResponse<AccountResponse> updateStatus(
            @Parameter(description = "Идентификатор аккаунта") @PathVariable("accountId") UUID accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ApiResponse.success(message(STATUS_UPDATED),
                accountAdminService.updateStatus(SecurityUtils.currentUserId(), accountId, request));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }
}
