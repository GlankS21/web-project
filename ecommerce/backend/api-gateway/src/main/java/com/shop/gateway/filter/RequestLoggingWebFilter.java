package com.shop.gateway.filter;

import com.shop.common.constant.LoggingConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Access log of every request handled by the gateway, including those rejected before routing
 * (401, 403, 429). Reactive pipelines hop threads, so the correlation id is bound to the MDC
 * explicitly around the log statement.
 */
@Slf4j
@Component
public class RequestLoggingWebFilter implements WebFilter, Ordered {

    private static final String HEALTH_PATH_PREFIX = "/actuator/health";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith(HEALTH_PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        long startNanos = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            String correlationId = exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE);
            try (MDC.MDCCloseable ignored = MDC.putCloseable(LoggingConstants.MDC_CORRELATION_ID, correlationId)) {
                log.info("{} {} -> {} ({} ms)",
                        exchange.getRequest().getMethod(),
                        path,
                        status != null ? status.value() : 200,
                        durationMs);
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
