package com.shop.auth.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param accessTokenTtl        lifetime of JWT access tokens (short: they cannot be revoked)
 * @param refreshTokenTtl       lifetime of opaque refresh tokens (renewed on every rotation)
 * @param passwordResetTtl      validity of the link e-mailed by "forgot password" (default 30 minutes)
 * @param passwordResetCooldown minimum delay between two reset e-mails for one account (default 1 minute)
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotNull Duration passwordResetTtl,
        @NotNull Duration passwordResetCooldown
) {

    public AuthProperties {
        passwordResetTtl = passwordResetTtl == null ? Duration.ofMinutes(30) : passwordResetTtl;
        passwordResetCooldown = passwordResetCooldown == null ? Duration.ofMinutes(1) : passwordResetCooldown;
    }
}
