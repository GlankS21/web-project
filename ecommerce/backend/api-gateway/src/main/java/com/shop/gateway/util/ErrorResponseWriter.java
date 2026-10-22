package com.shop.gateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.ErrorResponse;
import com.shop.common.exception.ErrorCode;
import com.shop.gateway.filter.CorrelationIdWebFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;
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
 * <p>
 * The message is a key of the {@link MessageSource} ({@code messages_ru.properties} of the gateway or
 * the shared {@code common-messages_ru.properties}), resolved for
 * {@link CommonMessagesAutoConfiguration#DEFAULT_LOCALE}: the gateway answers in Russian whatever the
 * {@code Accept-Language} of the request. An unknown key falls back to the generic text of the code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    /**
     * Writes the generic message of {@code code} ({@code error.<CODE>}).
     */
    public Mono<Void> write(ServerWebExchange exchange, ErrorCode code) {
        return write(exchange, code, ErrorCode.messageKeyOf(code));
    }

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String messageKey, Object... args) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        HttpStatus status = HttpStatus.valueOf(code.getHttpStatus());
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(serialize(build(exchange, code, messageKey, args)));
        return response.writeWith(Mono.just(buffer));
    }

    public ErrorResponse build(ServerWebExchange exchange, ErrorCode code, String messageKey, Object... args) {
        HttpStatus status = HttpStatus.valueOf(code.getHttpStatus());
        return ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code.name())
                .message(message(code, messageKey, args))
                .path(exchange.getRequest().getPath().value())
                .correlationId(exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE))
                .build();
    }

    /**
     * @return the Russian text of {@code messageKey}, else of {@code error.<CODE>}, else the English
     *         default of the code (only when no bundle is on the classpath)
     */
    public String message(ErrorCode code, String messageKey, Object... args) {
        DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable(
                new String[]{messageKey, ErrorCode.messageKeyOf(code)}, args, code.getDefaultMessage());
        return messageSource.getMessage(resolvable, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
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
