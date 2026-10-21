package com.shop.auth.security;

import com.shop.auth.entity.UserAccount;

import java.time.Instant;

/**
 * A freshly issued access/refresh token pair. {@code refreshToken} is the raw value handed to the
 * client exactly once; only its hash is persisted.
 */
public record IssuedTokens(
        UserAccount account,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}
