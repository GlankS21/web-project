package com.shop.gateway.config;

import com.shop.common.constant.HeaderConstants;
import com.shop.gateway.filter.RateLimitWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS source used by the Spring Security chain ({@code http.cors()}), so preflight requests are
 * answered before authentication.
 */
@Configuration
public class CorsConfig {

    public static final String CORS_SOURCE_BEAN = "gatewayCorsConfigurationSource";

    @Bean(CORS_SOURCE_BEAN)
    public CorsConfigurationSource gatewayCorsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                HeaderConstants.CORRELATION_ID));
        configuration.setExposedHeaders(List.of(
                HeaderConstants.CORRELATION_ID,
                RateLimitWebFilter.REMAINING_HEADER,
                HttpHeaders.RETRY_AFTER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(properties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
