package com.shop.auth.service;

import com.shop.auth.entity.UserAccount;
import com.shop.auth.security.IssuedTokens;

import java.util.UUID;

public interface TokenService {

    /**
     * Issues a new access token and a new refresh token (a new session) for the account.
     */
    IssuedTokens issueTokens(UserAccount account);

    /**
     * Exchanges a valid refresh token for a new token pair; the presented token becomes unusable.
     * Presenting an already-rotated token is treated as theft: every session of the account is revoked.
     */
    IssuedTokens rotate(String refreshToken);

    /**
     * Revokes a single refresh token (logout). Unknown or already revoked tokens are ignored.
     */
    void revoke(String refreshToken);

    /**
     * Revokes every active refresh token of the account (logout everywhere, password change...).
     */
    void revokeAll(UUID accountId);

    /**
     * Deletes expired refresh tokens and returns how many were removed.
     */
    int purgeExpired();
}
