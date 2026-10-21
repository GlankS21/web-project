package com.shop.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Paths reachable without an access token. Everything else requires a valid JWT;
 * fine-grained role checks are enforced again by each downstream service.
 *
 * @param publicPaths    paths open for every HTTP method (login, register, docs, health...)
 * @param publicGetPaths paths open for GET only (product catalogue browsing)
 */
@ConfigurationProperties(prefix = "app.gateway.security")
public record GatewaySecurityProperties(List<String> publicPaths, List<String> publicGetPaths) {

    public GatewaySecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        publicGetPaths = publicGetPaths == null ? List.of() : List.copyOf(publicGetPaths);
    }
}
