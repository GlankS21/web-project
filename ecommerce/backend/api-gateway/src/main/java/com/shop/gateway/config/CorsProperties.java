package com.shop.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * CORS policy applied at the gateway (downstream services do not emit CORS headers).
 *
 * @param allowedOrigins comma separated list, e.g. {@code http://localhost:3000,https://shop.example.com}
 * @param maxAge         how long browsers may cache the preflight response
 */
@ConfigurationProperties(prefix = "app.gateway.cors")
public record CorsProperties(List<String> allowedOrigins, Duration maxAge) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        maxAge = maxAge == null ? Duration.ofHours(1) : maxAge;
    }
}
