package com.shop.gateway.filter;

import com.shop.gateway.config.TrustedProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressWebFilterTest {

    private static final String SECRET = "proxy-secret-for-tests";

    private final ClientAddressWebFilter filter = new ClientAddressWebFilter(new TrustedProxyProperties(SECRET));

    @Test
    void usesTheForwardedAddressFromTheTrustedProxy() {
        ServerWebExchange forwarded = run(filter, request()
                .header(ClientAddressWebFilter.PROXY_SECRET_HEADER, SECRET)
                .header(ClientAddressWebFilter.CLIENT_IP_HEADER, "203.0.113.7"));

        assertThat(ClientAddressWebFilter.clientAddress(forwarded)).isEqualTo("203.0.113.7");
        assertThat(forwarded.getRequest().getHeaders())
                .doesNotContainKeys(ClientAddressWebFilter.PROXY_SECRET_HEADER, ClientAddressWebFilter.CLIENT_IP_HEADER);
    }

    @Test
    void ignoresTheForwardedAddressWithoutTheRightSecret() {
        ServerWebExchange spoofed = run(filter, request()
                .header(ClientAddressWebFilter.PROXY_SECRET_HEADER, "guess")
                .header(ClientAddressWebFilter.CLIENT_IP_HEADER, "203.0.113.7"));
        ServerWebExchange noSecret = run(filter, request()
                .header(ClientAddressWebFilter.CLIENT_IP_HEADER, "203.0.113.7"));

        assertThat(ClientAddressWebFilter.clientAddress(spoofed)).isEqualTo("10.0.0.5");
        assertThat(ClientAddressWebFilter.clientAddress(noSecret)).isEqualTo("10.0.0.5");
        assertThat(noSecret.getRequest().getHeaders()).doesNotContainKey(ClientAddressWebFilter.CLIENT_IP_HEADER);
    }

    @Test
    void rejectsMalformedAddressesAndDisabledProxies() {
        ServerWebExchange malformed = run(filter, request()
                .header(ClientAddressWebFilter.PROXY_SECRET_HEADER, SECRET)
                .header(ClientAddressWebFilter.CLIENT_IP_HEADER, "1.2.3.4, 5.6.7.8"));
        ServerWebExchange disabled = run(new ClientAddressWebFilter(new TrustedProxyProperties("")), request()
                .header(ClientAddressWebFilter.PROXY_SECRET_HEADER, "")
                .header(ClientAddressWebFilter.CLIENT_IP_HEADER, "203.0.113.7"));

        assertThat(ClientAddressWebFilter.clientAddress(malformed)).isEqualTo("10.0.0.5");
        assertThat(ClientAddressWebFilter.clientAddress(disabled)).isEqualTo("10.0.0.5");
    }

    @Test
    void acceptsIpv6() {
        ServerWebExchange forwarded = run(filter, request()
                .header(ClientAddressWebFilter.PROXY_SECRET_HEADER, SECRET)
                .header(ClientAddressWebFilter.CLIENT_IP_HEADER, "2001:db8::1"));

        assertThat(ClientAddressWebFilter.clientAddress(forwarded)).isEqualTo("2001:db8::1");
    }

    private static MockServerHttpRequest.BaseBuilder<?> request() {
        return MockServerHttpRequest.get("/api/products")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 51234));
    }

    private static ServerWebExchange run(ClientAddressWebFilter filter, MockServerHttpRequest.BaseBuilder<?> request) {
        AtomicReference<ServerWebExchange> passedOn = new AtomicReference<>();
        filter.filter(MockServerWebExchange.from(request), exchange -> {
            passedOn.set(exchange);
            return Mono.empty();
        }).block();
        return passedOn.get();
    }
}
