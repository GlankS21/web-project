package com.shop.auth.service.impl;

import com.shop.auth.config.AuthProperties;
import com.shop.auth.entity.RefreshToken;
import com.shop.auth.entity.UserAccount;
import com.shop.auth.repository.RefreshTokenRepository;
import com.shop.auth.security.IssuedTokens;
import com.shop.auth.security.JwtTokenProvider;
import com.shop.auth.security.OpaqueTokenGenerator;
import com.shop.auth.util.TokenHashUtils;
import com.shop.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private TokenServiceImpl tokenService;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        tokenService = new TokenServiceImpl(refreshTokenRepository, jwtTokenProvider, new OpaqueTokenGenerator(),
                new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(7)), Clock.fixed(NOW, ZoneOffset.UTC));
        account = UserAccount.builder().id(UUID.randomUUID()).email("john@example.com").enabled(true).build();
    }

    @Test
    void issueTokens_storesOnlyTheHashOfTheRefreshToken() {
        stubSaveAssigningId(UUID.randomUUID());
        when(jwtTokenProvider.generateAccessToken(account, NOW, NOW.plus(Duration.ofMinutes(15)))).thenReturn("jwt");

        IssuedTokens tokens = tokenService.issueTokens(account);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(tokens.accessToken()).isEqualTo("jwt");
        assertThat(tokens.refreshToken()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(TokenHashUtils.sha256Hex(tokens.refreshToken()))
                .isNotEqualTo(tokens.refreshToken());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(saved.getValue().getUserAccount()).isSameAs(account);
    }

    @Test
    void rotate_revokesPresentedTokenAndLinksItToTheReplacement() {
        RefreshToken current = refreshToken(NOW.plus(Duration.ofDays(1)), null);
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenHashUtils.sha256Hex("raw-token")))
                .thenReturn(Optional.of(current));
        UUID replacementId = UUID.randomUUID();
        stubSaveAssigningId(replacementId);
        when(jwtTokenProvider.generateAccessToken(eq(account), eq(NOW), any())).thenReturn("new-jwt");

        IssuedTokens tokens = tokenService.rotate("raw-token");

        assertThat(tokens.accessToken()).isEqualTo("new-jwt");
        assertThat(tokens.refreshToken()).isNotEqualTo("raw-token");
        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(current.getReplacedByTokenId()).isEqualTo(replacementId);
    }

    @Test
    void rotate_withAlreadyRevokedTokenRevokesEverySessionOfTheAccount() {
        RefreshToken reused = refreshToken(NOW.plus(Duration.ofDays(1)), NOW.minusSeconds(60));
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(reused));

        assertThatThrownBy(() -> tokenService.rotate("stolen-token"))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepository).revokeAllActiveByUserAccountId(account.getId(), NOW);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_withExpiredTokenFails() {
        RefreshToken expired = refreshToken(NOW.minusSeconds(1), null);
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> tokenService.rotate("expired-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_withUnknownTokenFails() {
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.rotate("unknown"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rotate_forDisabledAccountRevokesTokenAndFails() {
        account.setEnabled(false);
        RefreshToken current = refreshToken(NOW.plus(Duration.ofDays(1)), null);
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> tokenService.rotate("raw-token"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(current.isRevoked()).isTrue();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revoke_isIdempotentForUnknownTokens() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> tokenService.revoke("unknown")).doesNotThrowAnyException();
    }

    @Test
    void revoke_marksKnownTokenAsRevoked() {
        RefreshToken token = refreshToken(NOW.plus(Duration.ofDays(1)), null);
        when(refreshTokenRepository.findByTokenHash(TokenHashUtils.sha256Hex("raw"))).thenReturn(Optional.of(token));

        tokenService.revoke("raw");

        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    private RefreshToken refreshToken(Instant expiresAt, Instant revokedAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .userAccount(account)
                .tokenHash("hash")
                .createdAt(NOW.minus(Duration.ofDays(1)))
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
    }

    private void stubSaveAssigningId(UUID id) {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            token.setId(id);
            return token;
        });
    }
}
