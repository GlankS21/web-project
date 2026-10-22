package com.shop.gateway.security;

import com.shop.common.exception.ErrorCode;
import com.shop.gateway.util.ErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 401 response in the platform error format, for missing, expired or tampered access tokens.
 */
@Component
@RequiredArgsConstructor
public class JsonServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    static final String INVALID_TOKEN_KEY = "gateway.error.invalid-token";

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        String messageKey = ex instanceof InvalidBearerTokenException
                ? INVALID_TOKEN_KEY
                : ErrorCode.messageKeyOf(ErrorCode.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return errorResponseWriter.write(exchange, ErrorCode.UNAUTHORIZED, messageKey);
    }
}
