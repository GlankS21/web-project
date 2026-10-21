package com.shop.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.HeaderConstants;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtClaimNames;
import com.shop.common.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Boots the gateway without config-server, Eureka or downstream services and checks the edge
 * behaviour: authentication, error format, correlation id, CORS and rate limiting.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "eureka.client.enabled=false",
                "app.security.jwt.secret=" + GatewaySecurityIntegrationTest.SECRET,
                "app.security.jwt.issuer=test-issuer",
                "app.gateway.security.public-paths=/api/auth/login,/actuator/health",
                "app.gateway.security.public-get-paths=/api/products/**",
                "app.gateway.cors.allowed-origins=http://localhost:3000",
                "app.gateway.rate-limit.enabled=true",
                "app.gateway.rate-limit.default-policy.capacity=100",
                "app.gateway.rate-limit.default-policy.refill-tokens=100",
                "app.gateway.rate-limit.default-policy.refill-period=1s",
                "app.gateway.rate-limit.auth-policy.capacity=2",
                "app.gateway.rate-limit.auth-policy.refill-tokens=1",
                "app.gateway.rate-limit.auth-policy.refill-period=1h",
                "app.gateway.rate-limit.auth-paths=/api/auth/login"
        })
// With RANDOM_PORT, the injected WebTestClient sends real HTTP requests to the embedded server
// (required for CORS: the request URI must carry a host).
class GatewaySecurityIntegrationTest {

    static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIz";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void protectedPathWithoutTokenReturns401InPlatformFormat() {
        webTestClient.get().uri("/api/users/me")
                .header(HeaderConstants.CORRELATION_ID, "it-correlation-1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HeaderConstants.CORRELATION_ID, "it-correlation-1")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.path").isEqualTo("/api/users/me")
                .jsonPath("$.correlationId").isEqualTo("it-correlation-1");
    }

    @Test
    void tamperedTokenIsRejected() {
        webTestClient.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("access") + "x")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Access token is invalid or expired");
    }

    @Test
    void refreshTypeTokenCannotBeUsedAsAccessToken() {
        webTestClient.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("refresh"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void validTokenPassesAuthenticationAndReachesRouting() {
        // No route exists in this test context, so passing security ends in the gateway's 404.
        webTestClient.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("access"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void publicGetPathDoesNotRequireToken() {
        webTestClient.get().uri("/api/products/1")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void actuatorEndpointsOtherThanHealthRequireAdmin() {
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();

        webTestClient.get().uri("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("access"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void corsPreflightIsAnsweredForAllowedOrigin() {
        webTestClient.method(HttpMethod.OPTIONS).uri("/api/users/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");

        webTestClient.method(HttpMethod.OPTIONS).uri("/api/users/me")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void loginEndpointIsRateLimited() {
        for (int i = 0; i < 2; i++) {
            webTestClient.post().uri("/api/auth/login").exchange().expectStatus().isNotFound();
        }
        webTestClient.post().uri("/api/auth/login")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectBody()
                .jsonPath("$.code").isEqualTo("TOO_MANY_REQUESTS");
    }

    private String accessToken(String tokenType) {
        JwtProperties properties = new JwtProperties(SECRET, "test-issuer");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(HmacJwtSupport.secretKey(properties)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("test-issuer")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim(JwtClaimNames.EMAIL, "customer@example.com")
                .claim(JwtClaimNames.ROLES, List.of("ROLE_CUSTOMER"))
                .claim(JwtClaimNames.TOKEN_TYPE, tokenType)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(HmacJwtSupport.ALGORITHM).build(), claims))
                .getTokenValue();
    }
}
