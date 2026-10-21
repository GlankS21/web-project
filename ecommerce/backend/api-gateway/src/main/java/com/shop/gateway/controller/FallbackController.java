package com.shop.gateway.controller;

import com.shop.common.dto.ErrorResponse;
import com.shop.common.exception.ErrorCode;
import com.shop.gateway.util.ErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Set;

/**
 * Target of the route circuit breakers ({@code fallbackUri: forward:/fallback/<service>}).
 * Reached only through an internal forward, never routed from outside.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
@RequiredArgsConstructor
public class FallbackController {

    private final ErrorResponseWriter errorResponseWriter;

    @RequestMapping("/{service}")
    public Mono<ResponseEntity<ErrorResponse>> fallback(@PathVariable("service") String service,
                                                        ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        log.warn("Circuit breaker fallback for {} on {}: {}", service, originalPath(exchange),
                cause != null ? cause.toString() : "circuit open");

        ErrorResponse body = errorResponseWriter.build(exchange, ErrorCode.SERVICE_UNAVAILABLE,
                "%s is temporarily unavailable, please try again later".formatted(service));
        body.setPath(originalPath(exchange));
        return Mono.just(ResponseEntity.status(body.getStatus()).body(body));
    }

    private String originalPath(ServerWebExchange exchange) {
        Set<URI> originalUrls = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originalUrls != null && !originalUrls.isEmpty()) {
            return originalUrls.iterator().next().getPath();
        }
        return exchange.getRequest().getPath().value();
    }
}
