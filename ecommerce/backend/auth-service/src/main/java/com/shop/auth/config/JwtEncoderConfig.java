package com.shop.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signs access tokens with the same HS256 key that the gateway and all services use to verify them
 * (the decoder comes from common-lib).
 */
@Configuration
public class JwtEncoderConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(HmacJwtSupport.secretKey(jwtProperties)));
    }
}
