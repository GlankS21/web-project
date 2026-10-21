package com.shop.auth.dto.response;

/**
 * Tokens returned by register, login and refresh.
 *
 * @param expiresIn        access token lifetime in seconds
 * @param refreshExpiresIn refresh token lifetime in seconds
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        AccountResponse account
) {
}
