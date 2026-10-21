package com.shop.auth.service.impl;

import com.shop.auth.config.AuthProperties;
import com.shop.auth.dto.request.ChangePasswordRequest;
import com.shop.auth.dto.request.LoginRequest;
import com.shop.auth.dto.request.RefreshTokenRequest;
import com.shop.auth.dto.request.RegisterRequest;
import com.shop.auth.dto.response.AuthResponse;
import com.shop.auth.entity.Role;
import com.shop.auth.entity.RoleName;
import com.shop.auth.entity.UserAccount;
import com.shop.auth.mapper.AccountMapperImpl;
import com.shop.auth.repository.RoleRepository;
import com.shop.auth.repository.UserAccountRepository;
import com.shop.auth.security.IssuedTokens;
import com.shop.auth.service.TokenService;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.ConflictException;
import com.shop.common.exception.ForbiddenException;
import com.shop.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));
    private final AuthProperties authProperties = new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(7));

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userAccountRepository, roleRepository, tokenService, passwordEncoder,
                new AccountMapperImpl(), eventPublisher, authProperties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void register_createsCustomerAccountPublishesEventAndIssuesTokens() {
        UUID generatedId = UUID.randomUUID();
        when(userAccountRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_CUSTOMER))
                .thenReturn(Optional.of(new Role(1L, RoleName.ROLE_CUSTOMER)));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(generatedId);
            return account;
        });
        when(tokenService.issueTokens(any(UserAccount.class))).thenAnswer(invocation -> tokens(invocation.getArgument(0)));

        AuthResponse response = authService.register(
                new RegisterRequest("  John@Example.COM ", "Secret123", " John ", " Doe "));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.account().email()).isEqualTo("john@example.com");
        assertThat(response.account().roles()).containsExactly("ROLE_CUSTOMER");

        ArgumentCaptor<UserAccount> savedAccount = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(savedAccount.capture());
        assertThat(savedAccount.getValue().isEnabled()).isTrue();
        assertThat(savedAccount.getValue().getPasswordHash()).isNotEqualTo("Secret123");
        assertThat(passwordEncoder.matches("Secret123", savedAccount.getValue().getPasswordHash())).isTrue();

        ArgumentCaptor<UserRegisteredEvent> event = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(generatedId);
        assertThat(event.getValue().firstName()).isEqualTo("John");
        assertThat(event.getValue().lastName()).isEqualTo("Doe");
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userAccountRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("john@example.com", "Secret123", "John", "Doe")))
                .isInstanceOf(ConflictException.class);
        verify(userAccountRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void login_withValidCredentialsIssuesTokensAndRecordsLoginTime() {
        UserAccount account = account(true);
        when(userAccountRepository.findByEmail("john@example.com")).thenReturn(Optional.of(account));
        when(tokenService.issueTokens(account)).thenReturn(tokens(account));

        AuthResponse response = authService.login(new LoginRequest("JOHN@example.com", "Secret123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(account.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void login_withWrongPasswordFails() {
        when(userAccountRepository.findByEmail("john@example.com")).thenReturn(Optional.of(account(true)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("john@example.com", "Wrong1234")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
        verify(tokenService, never()).issueTokens(any());
    }

    @Test
    void login_withUnknownEmailFailsWithSameMessageAndStillChecksAPassword() {
        when(userAccountRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "Secret123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
        verify(passwordEncoder).matches(eq("Secret123"), anyString());
    }

    @Test
    void login_withDisabledAccountIsForbidden() {
        when(userAccountRepository.findByEmail("john@example.com")).thenReturn(Optional.of(account(false)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("john@example.com", "Secret123")))
                .isInstanceOf(ForbiddenException.class);
        verify(tokenService, never()).issueTokens(any());
    }

    @Test
    void refresh_delegatesRotationToTokenService() {
        UserAccount account = account(true);
        when(tokenService.rotate("old-refresh")).thenReturn(tokens(account));

        AuthResponse response = authService.refresh(new RefreshTokenRequest("old-refresh"));

        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.account().id()).isEqualTo(account.getId());
    }

    @Test
    void changePassword_withWrongCurrentPasswordFails() {
        UserAccount account = account(true);
        when(userAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.changePassword(account.getId(),
                new ChangePasswordRequest("Wrong1234", "NewSecret456")))
                .isInstanceOf(BadRequestException.class);
        verify(tokenService, never()).revokeAll(any());
    }

    @Test
    void changePassword_rejectsUnchangedPassword() {
        UserAccount account = account(true);
        when(userAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.changePassword(account.getId(),
                new ChangePasswordRequest("Secret123", "Secret123")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void changePassword_updatesHashAndRevokesAllSessions() {
        UserAccount account = account(true);
        when(userAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        authService.changePassword(account.getId(), new ChangePasswordRequest("Secret123", "NewSecret456"));

        assertThat(passwordEncoder.matches("NewSecret456", account.getPasswordHash())).isTrue();
        verify(tokenService).revokeAll(account.getId());
    }

    private UserAccount account(boolean enabled) {
        return UserAccount.builder()
                .id(UUID.randomUUID())
                .email("john@example.com")
                .passwordHash(new BCryptPasswordEncoder(4).encode("Secret123"))
                .enabled(enabled)
                .roles(new HashSet<>(Set.of(new Role(1L, RoleName.ROLE_CUSTOMER))))
                .build();
    }

    private IssuedTokens tokens(UserAccount account) {
        return new IssuedTokens(account, "access-token", NOW.plusSeconds(900),
                "refresh-token", NOW.plus(Duration.ofDays(7)));
    }
}
