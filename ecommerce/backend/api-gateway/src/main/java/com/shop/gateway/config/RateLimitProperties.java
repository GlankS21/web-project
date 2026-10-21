package com.shop.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Per-client-IP rate limiting.
 *
 * @param enabled       master switch
 * @param defaultPolicy limit applied to every request
 * @param authPolicy    stricter limit for credential endpoints (brute-force protection)
 * @param authPaths     path patterns that use {@code authPolicy}
 */
@ConfigurationProperties(prefix = "app.gateway.rate-limit")
public record RateLimitProperties(boolean enabled,
                                  RateLimitPolicy defaultPolicy,
                                  RateLimitPolicy authPolicy,
                                  List<String> authPaths) {

    public RateLimitProperties {
        authPaths = authPaths == null ? List.of() : List.copyOf(authPaths);
    }
}
