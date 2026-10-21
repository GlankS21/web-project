package com.shop.common.security;

import com.shop.common.constant.RoleConstants;
import com.shop.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Access to the authenticated caller in servlet-based services.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new UnauthorizedException();
        }
        Set<String> roles = jwtAuthentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(
                UUID.fromString(jwtAuthentication.getToken().getSubject()),
                jwtAuthentication.getToken().getClaimAsString(JwtClaimNames.EMAIL),
                roles);
    }

    public static UUID currentUserId() {
        return currentUser().id();
    }

    /**
     * For endpoints that are public but reveal more to administrators: anonymous callers are not admins.
     */
    public static boolean currentUserIsAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> RoleConstants.ROLE_ADMIN.equals(authority.getAuthority()));
    }
}
