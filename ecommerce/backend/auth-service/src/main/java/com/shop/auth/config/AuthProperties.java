package com.shop.auth.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param accessTokenTtl  lifetime of JWT access tokens (short: they cannot be revoked)
 * @param refreshTokenTtl lifetime of opaque refresh tokens (renewed on every rotation)
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl
) {
}
