package com.shop.gateway.security;

import com.shop.common.exception.ErrorCode;
import com.shop.gateway.util.ErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 403 response in the platform error format.
 */
@Component
@RequiredArgsConstructor
public class JsonServerAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return errorResponseWriter.write(exchange, ErrorCode.FORBIDDEN);
    }
}
