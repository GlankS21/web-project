package com.shop.auth.service.impl;

import com.shop.auth.dto.request.UpdateAccountStatusRequest;
import com.shop.auth.dto.request.UpdateRolesRequest;
import com.shop.auth.dto.response.AccountResponse;
import com.shop.auth.entity.Role;
import com.shop.auth.entity.RoleName;
import com.shop.auth.entity.UserAccount;
import com.shop.auth.mapper.AccountMapper;
import com.shop.auth.repository.RoleRepository;
import com.shop.auth.repository.UserAccountRepository;
import com.shop.auth.service.AccountAdminService;
import com.shop.auth.service.TokenService;
import com.shop.common.dto.PageResponse;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAdminServiceImpl implements AccountAdminService {

    static final String CANNOT_REMOVE_OWN_ADMIN = "auth.error.cannot-remove-own-admin";
    static final String CANNOT_DISABLE_OWN_ACCOUNT = "auth.error.cannot-disable-own-account";

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;
    private final AccountMapper accountMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> searchAccounts(String email, Pageable pageable) {
        Page<UserAccount> page = StringUtils.hasText(email)
                ? userAccountRepository.findByEmailContainingIgnoreCase(email.trim(), pageable)
                : userAccountRepository.findAll(pageable);
        return PageResponse.from(page, accountMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID accountId) {
        return accountMapper.toResponse(findAccount(accountId));
    }

    @Override
    @Transactional
    public AccountResponse updateRoles(UUID actorId, UUID accountId, UpdateRolesRequest request) {
        if (actorId.equals(accountId) && !request.roles().contains(RoleName.ROLE_ADMIN)) {
            throw new BusinessException(CANNOT_REMOVE_OWN_ADMIN);
        }
        UserAccount account = findAccount(accountId);
        List<Role> roles = roleRepository.findByNameIn(request.roles());
        if (roles.size() != request.roles().size()) {
            throw new IllegalStateException("Some requested roles are not seeded: " + request.roles());
        }
        account.setRoles(new HashSet<>(roles));
        log.info("Admin {} set roles {} on account {}", actorId, request.roles(), accountId);
        // New roles are embedded in the next access token, obtained at the next refresh.
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional
    public AccountResponse updateStatus(UUID actorId, UUID accountId, UpdateAccountStatusRequest request) {
        boolean enabled = request.enabled();
        if (actorId.equals(accountId) && !enabled) {
            throw new BusinessException(CANNOT_DISABLE_OWN_ACCOUNT);
        }
        UserAccount account = findAccount(accountId);
        account.setEnabled(enabled);
        if (!enabled) {
            // Existing access tokens stay valid until they expire (access-token-ttl).
            tokenService.revokeAll(accountId);
        }
        log.info("Admin {} {} account {}", actorId, enabled ? "enabled" : "disabled", accountId);
        return accountMapper.toResponse(account);
    }

    private UserAccount findAccount(UUID accountId) {
        return userAccountRepository.findById(accountId)
                .orElseThrow(() -> ResourceNotFoundException.of(AuthServiceImpl.ACCOUNT_NOT_FOUND));
    }
}
