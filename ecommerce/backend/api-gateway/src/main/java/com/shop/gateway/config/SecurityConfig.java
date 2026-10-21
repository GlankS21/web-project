package com.shop.gateway.config;

import com.shop.common.constant.RoleConstants;
import com.shop.gateway.security.JsonServerAccessDeniedHandler;
import com.shop.gateway.security.JsonServerAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * Stateless JWT verification at the edge. The decoder (HS256, issuer and token type checks)
 * comes from common-lib's {@code JwtReactiveSecurityAutoConfiguration}.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         GatewaySecurityProperties properties,
                                                         ReactiveJwtAuthenticationConverterAdapter jwtConverter,
                                                         JsonServerAuthenticationEntryPoint authenticationEntryPoint,
                                                         JsonServerAccessDeniedHandler accessDeniedHandler,
                                                         @Qualifier(CorsConfig.CORS_SOURCE_BEAN)
                                                         CorsConfigurationSource corsConfigurationSource) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Explicit source so the policy never depends on which CorsConfigurationSource bean is found.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> {
                    exchanges.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    if (!properties.publicPaths().isEmpty()) {
                        exchanges.pathMatchers(properties.publicPaths().toArray(String[]::new)).permitAll();
                    }
                    if (!properties.publicGetPaths().isEmpty()) {
                        exchanges.pathMatchers(HttpMethod.GET, properties.publicGetPaths().toArray(String[]::new))
                                .permitAll();
                    }
                    exchanges.pathMatchers("/actuator/**").hasAuthority(RoleConstants.ROLE_ADMIN);
                    exchanges.anyExchange().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
