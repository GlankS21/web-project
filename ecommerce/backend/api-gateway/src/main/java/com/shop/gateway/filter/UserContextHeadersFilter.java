package com.shop.gateway.filter;

import com.shop.common.constant.HeaderConstants;
import com.shop.common.security.JwtClaimNames;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Forwards the verified caller identity to downstream services as {@code X-User-*} headers.
 * Any client-supplied {@code X-User-*} header is always removed first so it cannot be spoofed.
 */
@Component
public class UserContextHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> withUserHeaders(exchange, authentication))
                .switchIfEmpty(Mono.fromSupplier(() -> withoutUserHeaders(exchange)))
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private ServerWebExchange withUserHeaders(ServerWebExchange exchange, JwtAuthenticationToken authentication) {
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        String email = authentication.getToken().getClaimAsString(JwtClaimNames.EMAIL);
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    removeUserHeaders(headers);
                    headers.set(HeaderConstants.USER_ID, authentication.getToken().getSubject());
                    headers.set(HeaderConstants.USER_ROLES, roles);
                    if (email != null) {
                        headers.set(HeaderConstants.USER_EMAIL, email);
                    }
                }))
                .build();
    }

    private ServerWebExchange withoutUserHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(request -> request.headers(this::removeUserHeaders))
                .build();
    }

    private void removeUserHeaders(HttpHeaders headers) {
        headers.remove(HeaderConstants.USER_ID);
        headers.remove(HeaderConstants.USER_EMAIL);
        headers.remove(HeaderConstants.USER_ROLES);
    }
}
