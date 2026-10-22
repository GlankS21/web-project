package com.shop.auth.service.impl;

import com.shop.auth.config.AuthProperties;
import com.shop.auth.dto.request.ChangePasswordRequest;
import com.shop.auth.dto.request.LoginRequest;
import com.shop.auth.dto.request.RefreshTokenRequest;
import com.shop.auth.dto.request.RegisterRequest;
import com.shop.auth.dto.response.AccountResponse;
import com.shop.auth.dto.response.AuthResponse;
import com.shop.auth.entity.Role;
import com.shop.auth.entity.RoleName;
import com.shop.auth.entity.UserAccount;
import com.shop.auth.mapper.AccountMapper;
import com.shop.auth.repository.RoleRepository;
import com.shop.auth.repository.UserAccountRepository;
import com.shop.auth.security.IssuedTokens;
import com.shop.auth.service.AuthService;
import com.shop.auth.service.TokenService;
import com.shop.auth.util.EmailUtils;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.ConflictException;
import com.shop.common.exception.ForbiddenException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    static final String EMAIL_TAKEN = "auth.error.email-taken";
    static final String INVALID_CREDENTIALS = "auth.error.invalid-credentials";
    static final String ACCOUNT_DISABLED = "auth.error.account-disabled";
    static final String CURRENT_PASSWORD_INCORRECT = "auth.error.current-password-incorrect";
    static final String SAME_PASSWORD = "auth.error.same-password";
    static final String ACCOUNT_NOT_FOUND = "auth.error.account-not-found";

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AccountMapper accountMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthProperties authProperties;
    private final Clock clock;

    /**
     * Hash checked when the email is unknown, so that response time does not reveal
     * whether an account exists.
     */
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserAccountRepository userAccountRepository,
                           RoleRepository roleRepository,
                           TokenService tokenService,
                           PasswordEncoder passwordEncoder,
                           AccountMapper accountMapper,
                           ApplicationEventPublisher eventPublisher,
                           AuthProperties authProperties,
                           Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.accountMapper = accountMapper;
        this.eventPublisher = eventPublisher;
        this.authProperties = authProperties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = EmailUtils.normalize(request.email());
        if (userAccountRepository.existsByEmail(email)) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Role ROLE_CUSTOMER is not seeded"));

        Instant now = clock.instant();
        UserAccount account = userAccountRepository.save(UserAccount.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .enabled(true)
                .lastLoginAt(now)
                .roles(new HashSet<>(Set.of(customerRole)))
                .build());

        // Relayed to RabbitMQ only after the transaction commits (see UserEventPublisher).
        eventPublisher.publishEvent(new UserRegisteredEvent(
                UUID.randomUUID(), now, account.getId(), email,
                request.firstName().trim(), request.lastName().trim()));
        log.info("Registered account {}", account.getId());

        return toAuthResponse(tokenService.issueTokens(account));
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserAccount account = userAccountRepository.findByEmail(EmailUtils.normalize(request.email()))
                .orElse(null);
        if (account == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (!account.isEnabled()) {
            throw new ForbiddenException(ACCOUNT_DISABLED);
        }
        account.setLastLoginAt(clock.instant());
        return toAuthResponse(tokenService.issueTokens(account));
    }

    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        return toAuthResponse(tokenService.rotate(request.refreshToken()));
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        tokenService.revoke(request.refreshToken());
    }

    @Override
    @Transactional
    public void logoutAll(UUID accountId) {
        tokenService.revokeAll(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID accountId) {
        return accountMapper.toResponse(findAccount(accountId));
    }

    @Override
    @Transactional
    public void changePassword(UUID accountId, ChangePasswordRequest request) {
        UserAccount account = findAccount(accountId);
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BadRequestException(CURRENT_PASSWORD_INCORRECT);
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BadRequestException(SAME_PASSWORD);
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Every other session must re-authenticate with the new password.
        tokenService.revokeAll(accountId);
        log.info("Password changed for account {}", accountId);
    }

    private UserAccount findAccount(UUID accountId) {
        return userAccountRepository.findById(accountId)
                .orElseThrow(() -> ResourceNotFoundException.of(ACCOUNT_NOT_FOUND));
    }

    private AuthResponse toAuthResponse(IssuedTokens tokens) {
        return new AuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                TOKEN_TYPE,
                authProperties.accessTokenTtl().toSeconds(),
                authProperties.refreshTokenTtl().toSeconds(),
                accountMapper.toResponse(tokens.account()));
    }
}
