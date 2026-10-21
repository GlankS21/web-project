package com.shop.auth.service.impl;

import com.shop.auth.dto.request.UpdateAccountStatusRequest;
import com.shop.auth.dto.request.UpdateRolesRequest;
import com.shop.auth.dto.response.AccountResponse;
import com.shop.auth.entity.Role;
import com.shop.auth.entity.RoleName;
import com.shop.auth.entity.UserAccount;
import com.shop.auth.mapper.AccountMapperImpl;
import com.shop.auth.repository.RoleRepository;
import com.shop.auth.repository.UserAccountRepository;
import com.shop.auth.service.TokenService;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAdminServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private TokenService tokenService;

    private AccountAdminServiceImpl service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AccountAdminServiceImpl(userAccountRepository, roleRepository, tokenService,
                new AccountMapperImpl());
    }

    @Test
    void updateRoles_preventsAdminFromRemovingOwnAdminRole() {
        assertThatThrownBy(() -> service.updateRoles(adminId, adminId,
                new UpdateRolesRequest(Set.of(RoleName.ROLE_CUSTOMER))))
                .isInstanceOf(BusinessException.class);
        verify(userAccountRepository, never()).findById(any());
    }

    @Test
    void updateRoles_replacesRolesOfAnotherAccount() {
        UserAccount customer = account(customerId);
        when(userAccountRepository.findById(customerId)).thenReturn(Optional.of(customer));
        Set<RoleName> requested = Set.of(RoleName.ROLE_CUSTOMER, RoleName.ROLE_ADMIN);
        when(roleRepository.findByNameIn(requested)).thenReturn(List.of(
                new Role(1L, RoleName.ROLE_CUSTOMER), new Role(2L, RoleName.ROLE_ADMIN)));

        AccountResponse response = service.updateRoles(adminId, customerId, new UpdateRolesRequest(requested));

        assertThat(response.roles()).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    void updateStatus_disablingAccountRevokesItsSessions() {
        UserAccount customer = account(customerId);
        when(userAccountRepository.findById(customerId)).thenReturn(Optional.of(customer));

        AccountResponse response = service.updateStatus(adminId, customerId, new UpdateAccountStatusRequest(false));

        assertThat(response.enabled()).isFalse();
        verify(tokenService).revokeAll(customerId);
    }

    @Test
    void updateStatus_enablingAccountKeepsSessions() {
        UserAccount customer = account(customerId);
        customer.setEnabled(false);
        when(userAccountRepository.findById(customerId)).thenReturn(Optional.of(customer));

        service.updateStatus(adminId, customerId, new UpdateAccountStatusRequest(true));

        assertThat(customer.isEnabled()).isTrue();
        verify(tokenService, never()).revokeAll(any());
    }

    @Test
    void updateStatus_preventsAdminFromDisablingThemselves() {
        assertThatThrownBy(() -> service.updateStatus(adminId, adminId, new UpdateAccountStatusRequest(false)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getAccount_unknownIdIsNotFound() {
        when(userAccountRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccount(customerId)).isInstanceOf(ResourceNotFoundException.class);
    }

    private UserAccount account(UUID id) {
        return UserAccount.builder()
                .id(id)
                .email("customer@example.com")
                .passwordHash("hash")
                .enabled(true)
                .roles(new HashSet<>(Set.of(new Role(1L, RoleName.ROLE_CUSTOMER))))
                .build();
    }
}
