package com.shop.auth.service.impl;

import com.shop.auth.config.AuthProperties;
import com.shop.auth.entity.RefreshToken;
import com.shop.auth.entity.UserAccount;
import com.shop.auth.repository.RefreshTokenRepository;
import com.shop.auth.security.IssuedTokens;
import com.shop.auth.security.JwtTokenProvider;
import com.shop.auth.security.OpaqueTokenGenerator;
import com.shop.auth.service.TokenService;
import com.shop.auth.util.TokenHashUtils;
import com.shop.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Override
    @Transactional
    public IssuedTokens issueTokens(UserAccount account) {
        return issue(account, clock.instant(), null);
    }

    /**
     * Not rolled back on {@link UnauthorizedException}: when token reuse is detected, the
     * revocation of all sessions must be committed even though the request fails.
     */
    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public IssuedTokens rotate(String refreshToken) {
        RefreshToken current = refreshTokenRepository
                .findByTokenHashForUpdate(TokenHashUtils.sha256Hex(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));
        Instant now = clock.instant();
        UserAccount account = current.getUserAccount();

        if (current.isRevoked()) {
            int revoked = refreshTokenRepository.revokeAllActiveByUserAccountId(account.getId(), now);
            log.warn("Reuse of revoked refresh token {} for account {}; revoked {} active session(s)",
                    current.getId(), account.getId(), revoked);
            throw new UnauthorizedException("Refresh token has been revoked, please log in again");
        }
        if (current.isExpired(now)) {
            throw new UnauthorizedException("Refresh token has expired, please log in again");
        }
        if (!account.isEnabled()) {
            current.revoke(now);
            throw new UnauthorizedException("Account is disabled");
        }
        return issue(account, now, current);
    }

    @Override
    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.findByTokenHash(TokenHashUtils.sha256Hex(refreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Override
    @Transactional
    public void revokeAll(UUID accountId) {
        int revoked = refreshTokenRepository.revokeAllActiveByUserAccountId(accountId, clock.instant());
        log.info("Revoked {} active session(s) of account {}", revoked, accountId);
    }

    @Override
    @Transactional
    public int purgeExpired() {
        return refreshTokenRepository.deleteExpiredBefore(clock.instant());
    }

    private IssuedTokens issue(UserAccount account, Instant now, RefreshToken replaced) {
        Instant accessTokenExpiresAt = now.plus(authProperties.accessTokenTtl());
        String accessToken = jwtTokenProvider.generateAccessToken(account, now, accessTokenExpiresAt);

        String rawRefreshToken = opaqueTokenGenerator.generate();
        RefreshToken refreshToken = refreshTokenRepository.save(RefreshToken.builder()
                .userAccount(account)
                .tokenHash(TokenHashUtils.sha256Hex(rawRefreshToken))
                .createdAt(now)
                .expiresAt(now.plus(authProperties.refreshTokenTtl()))
                .build());
        if (replaced != null) {
            replaced.replaceWith(refreshToken.getId(), now);
        }
        return new IssuedTokens(account, accessToken, accessTokenExpiresAt,
                rawRefreshToken, refreshToken.getExpiresAt());
    }
}
