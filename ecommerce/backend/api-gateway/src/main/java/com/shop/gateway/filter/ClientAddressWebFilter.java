package com.shop.gateway.filter;

import com.shop.gateway.config.TrustedProxyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Resolves the address of the calling client, used as rate-limit key. The direct peer is the client,
 * unless the request carries the trusted proxy secret: then the {@code X-Client-Ip} header sent by
 * the proxy is used. Both headers are always removed so they never reach downstream services, and a
 * client cannot choose its own rate-limit bucket without knowing the secret.
 */
@Slf4j
@Component
public class ClientAddressWebFilter implements WebFilter, Ordered {

    public static final String PROXY_SECRET_HEADER = "X-Proxy-Secret";
    public static final String CLIENT_IP_HEADER = "X-Client-Ip";
    public static final String CLIENT_ADDRESS_ATTRIBUTE = ClientAddressWebFilter.class.getName() + ".clientAddress";

    static final String UNKNOWN = "unknown";

    /** IPv4 or IPv6 literal; anything else is rejected rather than parsed. */
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private final TrustedProxyProperties properties;
    private final byte[] secret;

    public ClientAddressWebFilter(TrustedProxyProperties properties) {
        this.properties = properties;
        this.secret = properties.enabled() ? properties.secret().getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientAddress = fromTrustedProxy(request);
        if (clientAddress == null) {
            clientAddress = peerAddress(request);
        }
        ServerWebExchange mutated = exchange.mutate()
                .request(request.mutate().headers(headers -> {
                    headers.remove(PROXY_SECRET_HEADER);
                    headers.remove(CLIENT_IP_HEADER);
                }).build())
                .build();
        mutated.getAttributes().put(CLIENT_ADDRESS_ATTRIBUTE, clientAddress);
        return chain.filter(mutated);
    }

    /**
     * Right after the correlation id filter, before security and rate limiting.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    public static String clientAddress(ServerWebExchange exchange) {
        String address = exchange.getAttribute(CLIENT_ADDRESS_ATTRIBUTE);
        return address == null ? peerAddress(exchange.getRequest()) : address;
    }

    private String fromTrustedProxy(ServerHttpRequest request) {
        String presented = request.getHeaders().getFirst(PROXY_SECRET_HEADER);
        if (presented == null) {
            return null;
        }
        if (!properties.enabled()
                || !MessageDigest.isEqual(secret, presented.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Ignoring {} from {}: wrong secret or trusted proxy disabled", CLIENT_IP_HEADER,
                    peerAddress(request));
            return null;
        }
        String clientIp = request.getHeaders().getFirst(CLIENT_IP_HEADER);
        return clientIp != null && IP_LITERAL.matcher(clientIp).matches() ? clientIp : null;
    }

    private static String peerAddress(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return UNKNOWN;
        }
        return remoteAddress.getAddress().getHostAddress();
    }
}
