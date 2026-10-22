package com.shop.gateway.exception;

import com.shop.common.exception.ErrorCode;
import com.shop.gateway.util.ErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Converts every unhandled gateway error (unknown route, no available instance, connection
 * failure, timeout...) into the platform error format. Runs before Spring Boot's default handler.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    static final String ENDPOINT_NOT_FOUND_KEY = "error.request.endpoint-not-found";

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        ErrorCode code = resolve(ex);
        String path = exchange.getRequest().getPath().value();
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("Unhandled gateway error on {}", path, ex);
        } else {
            log.warn("Gateway error on {}: {} ({})", path, code, ex.getMessage());
        }
        if (code == ErrorCode.RESOURCE_NOT_FOUND) {
            return errorResponseWriter.write(exchange, code, ENDPOINT_NOT_FOUND_KEY);
        }
        return errorResponseWriter.write(exchange, code);
    }

    private ErrorCode resolve(Throwable ex) {
        if (ex instanceof ResponseStatusException statusException) {
            return switch (statusException.getStatusCode().value()) {
                case 400 -> ErrorCode.BAD_REQUEST;
                case 401 -> ErrorCode.UNAUTHORIZED;
                case 403 -> ErrorCode.FORBIDDEN;
                case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
                case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
                case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
                case 429 -> ErrorCode.TOO_MANY_REQUESTS;
                case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
                case 504 -> ErrorCode.GATEWAY_TIMEOUT;
                default -> statusException.getStatusCode().is5xxServerError()
                        ? ErrorCode.INTERNAL_ERROR
                        : ErrorCode.BAD_REQUEST;
            };
        }
        if (ex instanceof ConnectException) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }
        if (ex instanceof TimeoutException) {
            return ErrorCode.GATEWAY_TIMEOUT;
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}
