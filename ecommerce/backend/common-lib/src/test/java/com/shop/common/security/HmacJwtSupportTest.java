package com.shop.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacJwtSupportTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[64]);
    private static final JwtProperties PROPERTIES = new JwtProperties(SECRET, "test-issuer");

    private final NimbusJwtEncoder encoder =
            new NimbusJwtEncoder(new ImmutableSecret<>(HmacJwtSupport.secretKey(PROPERTIES)));
    private final JwtDecoder decoder = HmacJwtSupport.jwtDecoder(PROPERTIES);

    @Test
    void decodesValidAccessTokenAndMapsRoles() {
        UUID userId = UUID.randomUUID();
        String token = encode("test-issuer", JwtClaimNames.ACCESS_TOKEN_TYPE, userId);

        Jwt jwt = decoder.decode(token);
        AbstractAuthenticationToken authentication = HmacJwtSupport.jwtAuthenticationConverter().convert(jwt);

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString(JwtClaimNames.EMAIL)).isEqualTo("john@example.com");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void rejectsTokenWithWrongType() {
        String token = encode("test-issuer", "refresh", UUID.randomUUID());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        String token = encode("someone-else", JwtClaimNames.ACCESS_TOKEN_TYPE, UUID.randomUUID());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsShortOrNonBase64Secrets() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> HmacJwtSupport.secretKey(new JwtProperties(shortSecret, "i")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
        assertThatThrownBy(() -> HmacJwtSupport.secretKey(new JwtProperties("change-me!", "i")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
        assertThatThrownBy(() -> HmacJwtSupport.secretKey(new JwtProperties(" ", "i")))
                .isInstanceOf(IllegalStateException.class);
    }

    private String encode(String issuer, String tokenType, UUID subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim(JwtClaimNames.EMAIL, "john@example.com")
                .claim(JwtClaimNames.ROLES, List.of("ROLE_CUSTOMER"))
                .claim(JwtClaimNames.TOKEN_TYPE, tokenType)
                .build();
        JwsHeader header = JwsHeader.with(HmacJwtSupport.ALGORITHM).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
