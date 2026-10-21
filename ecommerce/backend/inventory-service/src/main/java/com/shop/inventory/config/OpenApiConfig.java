package com.shop.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI inventoryServiceOpenApi(@Value("${app.openapi.gateway-url:http://localhost:8080}") String gatewayUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("Inventory Service API")
                        .version("v1")
                        .description("Stock levels and movements (ROLE_ADMIN), public stock availability. "
                                + "Stock reservations are driven by order events on RabbitMQ."))
                .servers(List.of(new Server().url(gatewayUrl).description("API Gateway")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
