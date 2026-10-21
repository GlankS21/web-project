package com.shop.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtProperties;
import com.shop.common.security.RestAccessDeniedHandler;
import com.shop.common.security.RestAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Resource-server building blocks for servlet services that verify access tokens issued by auth-service.
 * Each service still declares its own {@code SecurityFilterChain} with its authorization rules.
 */
@AutoConfiguration(
        after = JacksonAutoConfiguration.class,
        before = OAuth2ResourceServerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({JwtDecoder.class, JwtAuthenticationConverter.class})
@ConditionalOnProperty(prefix = "app.security.jwt", name = "secret")
@EnableConfigurationProperties(JwtProperties.class)
public class JwtServletSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        return HmacJwtSupport.jwtDecoder(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        return HmacJwtSupport.jwtAuthenticationConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new RestAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper) {
        return new RestAccessDeniedHandler(objectMapper);
    }
}
