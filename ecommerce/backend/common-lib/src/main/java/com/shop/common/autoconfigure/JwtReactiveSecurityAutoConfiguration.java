package com.shop.common.autoconfigure;

import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;

/**
 * Reactive (WebFlux) counterpart of {@link JwtServletSecurityAutoConfiguration}, used by the API gateway.
 */
@AutoConfiguration(before = ReactiveOAuth2ResourceServerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({ReactiveJwtDecoder.class, ReactiveJwtAuthenticationConverterAdapter.class})
@ConditionalOnProperty(prefix = "app.security.jwt", name = "secret")
@EnableConfigurationProperties(JwtProperties.class)
public class JwtReactiveSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withSecretKey(HmacJwtSupport.secretKey(properties))
                .macAlgorithm(HmacJwtSupport.ALGORITHM)
                .build();
        decoder.setJwtValidator(HmacJwtSupport.accessTokenValidator(properties));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter() {
        return new ReactiveJwtAuthenticationConverterAdapter(HmacJwtSupport.jwtAuthenticationConverter());
    }
}
