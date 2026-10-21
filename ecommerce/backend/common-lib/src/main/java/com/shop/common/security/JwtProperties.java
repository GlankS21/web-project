package com.shop.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared JWT settings (HMAC-SHA256).
 *
 * @param secret Base64-encoded key of at least 256 bits, provided through {@code JWT_SECRET}
 * @param issuer value of the {@code iss} claim, written by auth-service and verified everywhere else
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, String issuer) {
}
