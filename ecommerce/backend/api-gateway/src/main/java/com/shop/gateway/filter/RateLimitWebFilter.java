package com.shop.gateway.filter;

import com.shop.common.exception.ErrorCode;
import com.shop.gateway.config.RateLimitPolicy;
import com.shop.gateway.config.RateLimitProperties;
import com.shop.gateway.ratelimit.ClientRateLimiter;
import com.shop.gateway.ratelimit.RateLimitDecision;
import com.shop.gateway.util.ErrorResponseWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Per-client-IP rate limiting; the client address is resolved by {@link ClientAddressWebFilter}. Runs after the Spring Security chain (order -100) so that
 * CORS headers are already present on 429 responses and browsers can read them.
 */
@Slf4j
@Component
public class RateLimitWebFilter implements WebFilter, Ordered {

    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private static final String DEFAULT_POLICY = "default";
    private static final String AUTH_POLICY = "auth";

    private final RateLimitProperties properties;
    private final ClientRateLimiter rateLimiter;
    private final ErrorResponseWriter errorResponseWriter;
    private final List<PathPattern> authPathPatterns;

    public RateLimitWebFilter(RateLimitProperties properties,
                              ClientRateLimiter rateLimiter,
                              ErrorResponseWriter errorResponseWriter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.errorResponseWriter = errorResponseWriter;
        this.authPathPatterns = properties.authPaths().stream()
                .map(PathPatternParser.defaultInstance::parse)
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.enabled() || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        boolean authRequest = isAuthPath(exchange.getRequest().getPath().pathWithinApplication());
        String policyName = authRequest ? AUTH_POLICY : DEFAULT_POLICY;
        RateLimitPolicy policy = authRequest ? properties.authPolicy() : properties.defaultPolicy();
        String clientKey = ClientAddressWebFilter.clientAddress(exchange);

        RateLimitDecision decision = rateLimiter.tryAcquire(policyName, policy, clientKey);
        exchange.getResponse().getHeaders().set(REMAINING_HEADER, String.valueOf(decision.remaining()));
        if (decision.allowed()) {
            return chain.filter(exchange);
        }

        log.warn("Rate limit '{}' exceeded for client {} on {}", policyName, clientKey,
                exchange.getRequest().getPath().value());
        long retryAfterSeconds = Math.max(1, policy.refillPeriod().toSeconds() / policy.refillTokens());
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return errorResponseWriter.write(exchange, ErrorCode.TOO_MANY_REQUESTS);
    }

    @Override
    public int getOrder() {
        return 10;
    }

    private boolean isAuthPath(PathContainer path) {
        return authPathPatterns.stream().anyMatch(pattern -> pattern.matches(path));
    }
}
