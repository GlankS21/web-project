package com.shop.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.event.ProductCreatedEvent;
import com.shop.common.event.StockReleasedEvent;
import com.shop.common.event.StockReservationFailedEvent;
import com.shop.common.event.StockReservedEvent;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtClaimNames;
import com.shop.common.security.JwtProperties;
import com.shop.inventory.repository.InventoryItemRepository;
import com.shop.inventory.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * inventory-service side of the order saga against real PostgreSQL and RabbitMQ: order events go in
 * through the listeners, replies come out through the outbox relay. Each test uses its own seeded
 * products so that the tests do not affect each other.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.security.jwt.secret=" + InventorySagaIntegrationTest.SECRET,
        "app.security.jwt.issuer=test-issuer",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.inventory.outbox.poll-interval=200ms"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InventorySagaIntegrationTest {

    static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIz";
    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.0-management-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private TopicExchange inventoryExchange;
    @Autowired
    private InventoryItemRepository inventoryItemRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void reservesThenCommitsStockOnConfirmation() throws Exception {
        String probe = probe(MessagingConstants.STOCK_RESERVED_KEY);
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent created = orderCreated(orderId, "ORD-COMMIT", line(19L, 5), line(18L, 2));

        publishOrderEvent(MessagingConstants.ORDER_CREATED_KEY, created);

        StockReservedEvent reserved = receive(probe, new ParameterizedTypeReference<>() {
        });
        assertThat(reserved).isNotNull();
        assertThat(reserved.orderId()).isEqualTo(orderId);
        assertThat(stock(19L).at("/data/quantityReserved").asInt()).isEqualTo(5);
        assertThat(stock(19L).at("/data/availableQuantity").asInt()).isEqualTo(195);

        // A redelivered order.created changes nothing and is not answered twice
        publishOrderEvent(MessagingConstants.ORDER_CREATED_KEY, created);
        assertThat(rabbitTemplate.receive(probe, 1_500)).isNull();
        assertThat(stock(19L).at("/data/quantityReserved").asInt()).isEqualTo(5);

        publishOrderEvent(MessagingConstants.ORDER_CONFIRMED_KEY, new OrderConfirmedEvent(UUID.randomUUID(),
                Instant.now(), orderId, "ORD-COMMIT", UUID.randomUUID(), "jane@example.com", BigDecimal.TEN, "VND"));
        waitUntil(() -> inventoryItemRepository.findById(19L).orElseThrow().getQuantityOnHand() == 195);

        assertThat(stock(19L).at("/data/quantityReserved").asInt()).isZero();
        assertThat(stock(18L).at("/data/quantityOnHand").asInt()).isEqualTo(78);
        mockMvc.perform(get("/api/inventory/reservations/" + orderId).header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                .andExpect(jsonPath("$.data.items.length()").value(2));
        mockMvc.perform(get("/api/inventory/19/movements").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("SALE"))
                .andExpect(jsonPath("$.data.content[0].onHandAfter").value(195))
                .andExpect(jsonPath("$.data.content[1].type").value("RESERVATION"));
    }

    @Test
    void insufficientStockFailsTheWholeReservation() throws Exception {
        String probe = probe(MessagingConstants.STOCK_RESERVATION_FAILED_KEY);
        UUID orderId = UUID.randomUUID();

        // Product 17 is out of stock in the seed data; product 16 must not be reserved either
        publishOrderEvent(MessagingConstants.ORDER_CREATED_KEY,
                orderCreated(orderId, "ORD-FAIL", line(16L, 1), line(17L, 1)));

        StockReservationFailedEvent failed = receive(probe, new ParameterizedTypeReference<>() {
        });
        assertThat(failed).isNotNull();
        assertThat(failed.orderId()).isEqualTo(orderId);
        assertThat(failed.reason()).contains("product 17 (requested 1, available 0)");
        assertThat(stock(16L).at("/data/quantityReserved").asInt()).isZero();
    }

    @Test
    void cancellationReleasesReservedStock() throws Exception {
        String reservedProbe = probe(MessagingConstants.STOCK_RESERVED_KEY);
        String releasedProbe = probe(MessagingConstants.STOCK_RELEASED_KEY);
        UUID orderId = UUID.randomUUID();

        publishOrderEvent(MessagingConstants.ORDER_CREATED_KEY, orderCreated(orderId, "ORD-CANCEL", line(21L, 4)));
        assertThat(this.<StockReservedEvent>receive(reservedProbe, new ParameterizedTypeReference<>() {
        })).isNotNull();
        assertThat(stock(21L).at("/data/quantityReserved").asInt()).isEqualTo(4);

        publishOrderEvent(MessagingConstants.ORDER_CANCELLED_KEY, new OrderCancelledEvent(UUID.randomUUID(),
                Instant.now(), orderId, "ORD-CANCEL", UUID.randomUUID(), "jane@example.com", "Payment declined"));

        StockReleasedEvent released = receive(releasedProbe, new ParameterizedTypeReference<>() {
        });
        assertThat(released).isNotNull();
        assertThat(released.orderId()).isEqualTo(orderId);
        assertThat(stock(21L).at("/data/quantityReserved").asInt()).isZero();
        assertThat(stock(21L).at("/data/quantityOnHand").asInt()).isEqualTo(70);
        waitUntil(() -> outboxEventRepository.countByPublishedAtIsNull() == 0);
    }

    @Test
    void productCreatedOpensAnEmptyStockRecord() throws Exception {
        rabbitTemplate.convertAndSend(MessagingConstants.PRODUCT_EXCHANGE, MessagingConstants.PRODUCT_CREATED_KEY,
                new ProductCreatedEvent(UUID.randomUUID(), Instant.now(), 500L, "TST-500", "Test product"));

        waitUntil(() -> inventoryItemRepository.existsById(500L));
        assertThat(stock(500L).at("/data/sku").asText()).isEqualTo("TST-500");
        assertThat(stock(500L).at("/data/quantityOnHand").asInt()).isZero();

        mockMvc.perform(post("/api/inventory/500/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantityChange", 12, "reason", "Received"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableQuantity").value(12));
    }

    @Test
    void availabilityIsPublicWhileManagementRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/inventory/availability").param("productIds", "1,17,999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].availableQuantity").value(50))
                .andExpect(jsonPath("$.data[0].inStock").value(true))
                .andExpect(jsonPath("$.data[1].inStock").value(false))
                .andExpect(jsonPath("$.data[2].productId").value(999));

        mockMvc.perform(get("/api/inventory")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inventory").header(HttpHeaders.AUTHORIZATION, token("ROLE_CUSTOMER")))
                .andExpect(status().isForbidden());

        // Seed data: products 6 and 10 are low, 17, 23 and 24 are empty
        mockMvc.perform(get("/api/inventory").param("lowStock", "true").param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.productId == 6)]").exists())
                .andExpect(jsonPath("$.data.content[?(@.productId == 10)]").exists())
                .andExpect(jsonPath("$.data.content[?(@.productId == 1)]").doesNotExist());

        // Removing reserved or non-existent units is refused
        mockMvc.perform(post("/api/inventory/17/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantityChange", -1, "reason", "Lost"))))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * Not auto-delete: {@code receive} with a timeout cancels its consumer, which would delete the queue.
     */
    private String probe(String routingKey) {
        Queue queue = new Queue("test.probe." + UUID.randomUUID(), false, false, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(inventoryExchange).with(routingKey));
        return queue.getName();
    }

    private <T> T receive(String queue, ParameterizedTypeReference<T> type) {
        return rabbitTemplate.receiveAndConvert(queue, RECEIVE_TIMEOUT_MS, type);
    }

    private void publishOrderEvent(String routingKey, Object event) {
        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, routingKey, event);
    }

    private JsonNode stock(Long productId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/inventory/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private OrderCreatedEvent orderCreated(UUID orderId, String orderNumber, OrderItemPayload... lines) {
        return new OrderCreatedEvent(UUID.randomUUID(), Instant.now(), orderId, orderNumber, UUID.randomUUID(),
                "jane@example.com", List.of(lines), BigDecimal.TEN, "VND");
    }

    private OrderItemPayload line(Long productId, int quantity) {
        return new OrderItemPayload(productId, "SKU-" + productId, "Product " + productId, quantity, BigDecimal.ONE);
    }

    private void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within 15 seconds");
            }
            Thread.sleep(200);
        }
    }

    private String adminToken() {
        return token("ROLE_ADMIN");
    }

    private String token(String role) {
        JwtProperties properties = new JwtProperties(SECRET, "test-issuer");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(HmacJwtSupport.secretKey(properties)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("test-issuer")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim(JwtClaimNames.EMAIL, "admin@example.com")
                .claim(JwtClaimNames.ROLES, List.of(role))
                .claim(JwtClaimNames.TOKEN_TYPE, JwtClaimNames.ACCESS_TOKEN_TYPE)
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(HmacJwtSupport.ALGORITHM).build(), claims)).getTokenValue();
    }
}
