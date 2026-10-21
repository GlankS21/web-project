package com.shop.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A reverse proxy in front of the gateway (the frontend's server side) that forwards the address
 * of the real client. Without it every request relayed by the proxy would share one rate-limit bucket.
 *
 * @param secret shared secret the proxy sends in {@code X-Proxy-Secret}; blank disables the feature
 */
@ConfigurationProperties(prefix = "app.gateway.trusted-proxy")
public record TrustedProxyProperties(String secret) {

    public boolean enabled() {
        return secret != null && !secret.isBlank();
    }
}
