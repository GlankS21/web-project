package com.shop.common.constant;

/**
 * Role names. Authorities carry the {@code ROLE_} prefix, plain role names do not.
 */
public final class RoleConstants {

    public static final String ROLE_PREFIX = "ROLE_";

    public static final String CUSTOMER = "CUSTOMER";
    public static final String ADMIN = "ADMIN";

    public static final String ROLE_CUSTOMER = ROLE_PREFIX + CUSTOMER;
    public static final String ROLE_ADMIN = ROLE_PREFIX + ADMIN;

    private RoleConstants() {
    }
}
