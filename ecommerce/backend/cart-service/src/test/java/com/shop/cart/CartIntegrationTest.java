package com.shop.cart;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.dto.ApiResponse;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.exception.ServiceUnavailableException;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtClaimNames;
import com.shop.common.security.JwtProperties;
import com.shop.cart.client.InventoryClient;
import com.shop.cart.client.ProductClient;
import com.shop.cart.client.ProductSummary;
import com.shop.cart.client.StockAvailability;
import com.shop.cart.entity.CartItem;
import com.shop.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cart API and the {@code order.created} consumer against real PostgreSQL and RabbitMQ;
 * product-service and inventory-service are mocked at the Feign client level.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.security.jwt.secret=" + CartIntegrationTest.SECRET,
        "app.security.jwt.issuer=test-issuer",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cart.max-items=3",
        "app.cart.max-quantity-per-item=10"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CartIntegrationTest {

    static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIz";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.0-management-alpine");

    @MockitoBean
    private ProductClient productClient;
    @MockitoBean
    private InventoryClient inventoryClient;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private CartRepository cartRepository;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void services() {
        catalogue("24990000");
        when(inventoryClient.getAvailability(anyList())).thenReturn(ApiResponse.success(List.of(
                new StockAvailability(7L, 3, true), new StockAvailability(19L, 50, true),
                new StockAvailability(23L, 0, false))));
    }

    @Test
    void customerFillsTheirCartWithinStockAndLimits() throws Exception {
        add(7, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value("AVAILABLE"));
        add(7, 1).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].quantity").value(3));
        add(7, 1).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only 3 unit(s) of MacBook Air M2 are available"));
        add(23, 1).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("USB hub is out of stock"));
        add(99, 1).andExpect(status().isUnprocessableEntity());
        add(19, 11).andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/cart/items/19").header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isNotFound());
        add(19, 1).andExpect(status().isOk());
        mockMvc.perform(put("/api/cart/items/19").header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuantity").value(7))
                .andExpect(jsonPath("$.data.subtotal").value(75570000))
                .andExpect(jsonPath("$.data.checkoutReady").value(true));

        // The price drops after the line was added; the stock of product 7 goes down to 1
        catalogue("22990000");
        when(inventoryClient.getAvailability(anyList())).thenReturn(ApiResponse.success(List.of(
                new StockAvailability(7L, 1, true), new StockAvailability(19L, 50, true))));
        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(22990000))
                .andExpect(jsonPath("$.data.items[0].priceChanged").value(true))
                .andExpect(jsonPath("$.data.items[0].status").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.data.checkoutReady").value(false));

        mockMvc.perform(delete("/api/cart/items/7").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mockMvc.perform(delete("/api/cart").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.checkoutReady").value(false));
        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    void cartStillShowsWhenTheCatalogueIsDown() throws Exception {
        add(19, 2).andExpect(status().isOk());

        when(productClient.getProductsByIds(anyList()))
                .thenThrow(new ServiceUnavailableException("product-service is unavailable"));
        when(inventoryClient.getAvailability(anyList()))
                .thenThrow(new ServiceUnavailableException("inventory-service is unavailable"));

        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(150000))
                .andExpect(jsonPath("$.data.items[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.pricesUpToDate").value(false));
        add(7, 1).andExpect(status().isServiceUnavailable());
    }

    @Test
    void placedOrderRemovesTheOrderedProducts() throws Exception {
        add(7, 1).andExpect(status().isOk());
        add(19, 2).andExpect(status().isOk());

        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_CREATED_KEY,
                new OrderCreatedEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "ORD-20261021-ABCDEF",
                        customerId, "jane@example.com",
                        List.of(new OrderItemPayload(7L, "LAP-MBA-M2", "MacBook Air M2", 1, new BigDecimal("24990000"))),
                        new BigDecimal("24990000"), "VND"));

        long deadline = System.currentTimeMillis() + 15_000;
        while (cartRepository.findByUserId(customerId).orElseThrow().getItems().size() != 1) {
            assertThat(System.currentTimeMillis()).as("cart updated within 15 seconds").isLessThan(deadline);
            Thread.sleep(200);
        }
        assertThat(cartRepository.findByUserId(customerId).orElseThrow().getItems())
                .extracting(CartItem::getProductId).containsExactly(19L);
    }

    private ResultActions add(long productId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/cart/items").header(HttpHeaders.AUTHORIZATION, token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity)));
    }

    private void catalogue(String macBookPrice) {
        // product 99 is not sold: it is missing from the response
        when(productClient.getProductsByIds(anyList())).thenReturn(ApiResponse.success(List.of(
                new ProductSummary(7L, "LAP-MBA-M2", "MacBook Air M2", new BigDecimal(macBookPrice), "VND",
                        "https://img/7.jpg", "ACTIVE"),
                new ProductSummary(19L, "ACC-UGR-C2C", "USB-C cable", new BigDecimal("150000"), "VND", null,
                        "ACTIVE"),
                new ProductSummary(23L, "ACC-HUB", "USB hub", new BigDecimal("390000"), "VND", null, "ACTIVE"))));
    }

    private String token() {
        JwtProperties properties = new JwtProperties(SECRET, "test-issuer");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(HmacJwtSupport.secretKey(properties)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("test-issuer")
                .subject(customerId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim(JwtClaimNames.EMAIL, "jane@example.com")
                .claim(JwtClaimNames.ROLES, List.of("ROLE_CUSTOMER"))
                .claim(JwtClaimNames.TOKEN_TYPE, JwtClaimNames.ACCESS_TOKEN_TYPE)
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(HmacJwtSupport.ALGORITHM).build(), claims)).getTokenValue();
    }
}
