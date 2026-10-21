package com.shop.common.security;

/**
 * Custom claims carried by access tokens issued by auth-service.
 * Standard claims used as well: {@code sub} (user id), {@code iss}, {@code iat}, {@code exp}, {@code jti}.
 */
public final class JwtClaimNames {

    public static final String EMAIL = "email";
    public static final String ROLES = "roles";
    public static final String TOKEN_TYPE = "token_type";

    public static final String ACCESS_TOKEN_TYPE = "access";

    private JwtClaimNames() {
    }
}
