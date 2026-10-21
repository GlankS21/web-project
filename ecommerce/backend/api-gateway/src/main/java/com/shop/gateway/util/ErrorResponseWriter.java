package com.shop.gateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.dto.ErrorResponse;
import com.shop.common.exception.ErrorCode;
import com.shop.gateway.filter.CorrelationIdWebFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Writes the platform {@link ErrorResponse} JSON body for errors produced by the gateway itself
 * (authentication, rate limiting, routing failures, circuit breaker fallbacks).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        HttpStatus status = HttpStatus.valueOf(code.getHttpStatus());
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(serialize(build(exchange, code, message)));
        return response.writeWith(Mono.just(buffer));
    }

    public ErrorResponse build(ServerWebExchange exchange, ErrorCode code, String message) {
        HttpStatus status = HttpStatus.valueOf(code.getHttpStatus());
        return ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code.name())
                .message(message)
                .path(exchange.getRequest().getPath().value())
                .correlationId(exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE))
                .build();
    }

    private byte[] serialize(ErrorResponse body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            log.error("Could not serialize error response", ex);
            return ("{\"status\":" + body.getStatus() + ",\"code\":\"" + body.getCode() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
