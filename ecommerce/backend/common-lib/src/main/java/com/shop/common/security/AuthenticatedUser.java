package com.shop.common.security;

import com.shop.common.constant.RoleConstants;

import java.util.Set;
import java.util.UUID;

/**
 * The caller identity extracted from a validated access token.
 */
public record AuthenticatedUser(UUID id, String email, Set<String> roles) {

    public boolean isAdmin() {
        return roles.contains(RoleConstants.ROLE_ADMIN);
    }
}
