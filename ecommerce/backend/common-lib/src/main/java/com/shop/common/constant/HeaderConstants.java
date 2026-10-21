package com.shop.common.constant;

/**
 * HTTP header names shared by the gateway and all downstream services.
 */
public final class HeaderConstants {

    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private HeaderConstants() {
    }
}
