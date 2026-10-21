package com.shop.auth.service;

import com.shop.auth.dto.request.UpdateAccountStatusRequest;
import com.shop.auth.dto.request.UpdateRolesRequest;
import com.shop.auth.dto.response.AccountResponse;
import com.shop.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Account administration (ROLE_ADMIN only).
 */
public interface AccountAdminService {

    PageResponse<AccountResponse> searchAccounts(String email, Pageable pageable);

    AccountResponse getAccount(UUID accountId);

    AccountResponse updateRoles(UUID actorId, UUID accountId, UpdateRolesRequest request);

    AccountResponse updateStatus(UUID actorId, UUID accountId, UpdateAccountStatusRequest request);
}
