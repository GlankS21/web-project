package com.shop.gateway.filter;

import com.shop.common.constant.HeaderConstants;
import com.shop.common.util.CorrelationIdUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * First filter of the gateway: accepts a safe incoming {@code X-Correlation-Id} or generates one,
 * forwards it to the downstream service and returns it to the client.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    public static final String CORRELATION_ID_ATTRIBUTE = CorrelationIdWebFilter.class.getName() + ".correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = CorrelationIdUtils.resolveOrGenerate(
                exchange.getRequest().getHeaders().getFirst(HeaderConstants.CORRELATION_ID));

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HeaderConstants.CORRELATION_ID, correlationId)
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        mutated.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);
        mutated.getResponse().getHeaders().set(HeaderConstants.CORRELATION_ID, correlationId);
        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
