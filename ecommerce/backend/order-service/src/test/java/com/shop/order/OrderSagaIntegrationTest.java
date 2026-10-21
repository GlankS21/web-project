package com.shop.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.dto.ApiResponse;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.PaymentFailedEvent;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.common.event.PaymentSucceededEvent;
import com.shop.common.event.StockReservedEvent;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtClaimNames;
import com.shop.common.security.JwtProperties;
import com.shop.order.client.ProductClient;
import com.shop.order.client.ProductSummary;
import com.shop.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * order-service side of the saga against real PostgreSQL and RabbitMQ: orders are placed through the
 * API, their events leave through the outbox, and the replies of inventory-service and
 * payment-service are simulated by publishing them on their exchanges. product-service is mocked.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.security.jwt.secret=" + OrderSagaIntegrationTest.SECRET,
        "app.security.jwt.issuer=test-issuer",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.order.outbox.poll-interval=200ms",
        "app.order.shipping.flat-fee=30000",
        "app.order.shipping.free-threshold=500000"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderSagaIntegrationTest {

    static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIz";
    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.0-management-alpine");

    @MockitoBean
    private ProductClient productClient;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private TopicExchange orderExchange;
    @Autowired
    private OrderRepository orderRepository;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void catalogue() {
        when(productClient.getProductsByIds(anyList())).thenReturn(ApiResponse.success(List.of(
                new ProductSummary(7L, "LAP-MBA-M2", "MacBook Air M2", new BigDecimal("24990000"), "VND",
                        "https://img/7.jpg", "ACTIVE"),
                new ProductSummary(19L, "ACC-UGR-C2C", "USB-C cable", new BigDecimal("150000"), "VND", null,
                        "ACTIVE"))));
    }

    @Test
    void successfulSagaEndsWithAPaidOrder() throws Exception {
        String created = probe(MessagingConstants.ORDER_CREATED_KEY);
        String paymentRequested = probe(MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY);
        String confirmed = probe(MessagingConstants.ORDER_CONFIRMED_KEY);

        JsonNode order = placeOrder(customerToken(), null);
        UUID orderId = UUID.fromString(order.at("/data/id").asText());
        assertThat(order.at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(order.at("/data/totalAmount").decimalValue()).isEqualByComparingTo("25140000");
        assertThat(order.at("/data/shippingFee").decimalValue()).isEqualByComparingTo("0");

        OrderCreatedEvent createdEvent = receive(created, new ParameterizedTypeReference<>() {
        });
        assertThat(createdEvent).isNotNull();
        assertThat(createdEvent.orderId()).isEqualTo(orderId);
        assertThat(createdEvent.userId()).isEqualTo(customerId);
        assertThat(createdEvent.items()).hasSize(2);

        rabbitTemplate.convertAndSend(MessagingConstants.INVENTORY_EXCHANGE, MessagingConstants.STOCK_RESERVED_KEY,
                new StockReservedEvent(UUID.randomUUID(), Instant.now(), orderId, createdEvent.orderNumber()));
        PaymentRequestedEvent paymentRequest = receive(paymentRequested, new ParameterizedTypeReference<>() {
        });
        assertThat(paymentRequest).isNotNull();
        assertThat(paymentRequest.amount()).isEqualByComparingTo("25140000");
        assertThat(paymentRequest.paymentMethod()).isEqualTo("CARD");
        assertThat(statusOf(orderId)).isEqualTo("AWAITING_PAYMENT");

        rabbitTemplate.convertAndSend(MessagingConstants.PAYMENT_EXCHANGE, MessagingConstants.PAYMENT_SUCCEEDED_KEY,
                new PaymentSucceededEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), orderId,
                        createdEvent.orderNumber(), "TXN-123", paymentRequest.amount(), "VND"));
        OrderConfirmedEvent confirmedEvent = receive(confirmed, new ParameterizedTypeReference<>() {
        });
        assertThat(confirmedEvent).isNotNull();
        assertThat(confirmedEvent.orderId()).isEqualTo(orderId);

        mockMvc.perform(get("/api/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paymentTransactionId").value("TXN-123"))
                .andExpect(jsonPath("$.data.statusHistory.length()").value(3));

        // Delivery flow, admin only
        mockMvc.perform(patch("/api/orders/admin/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/orders/admin/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\",\"note\":\"Tracking VN123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }

    @Test
    void failedPaymentCancelsTheOrder() throws Exception {
        String cancelled = probe(MessagingConstants.ORDER_CANCELLED_KEY);
        JsonNode order = placeOrder(customerToken(), null);
        UUID orderId = UUID.fromString(order.at("/data/id").asText());
        String orderNumber = order.at("/data/orderNumber").asText();

        rabbitTemplate.convertAndSend(MessagingConstants.INVENTORY_EXCHANGE, MessagingConstants.STOCK_RESERVED_KEY,
                new StockReservedEvent(UUID.randomUUID(), Instant.now(), orderId, orderNumber));
        waitUntil(() -> "AWAITING_PAYMENT".equals(orderRepository.findById(orderId).orElseThrow().getStatus().name()));
        rabbitTemplate.convertAndSend(MessagingConstants.PAYMENT_EXCHANGE, MessagingConstants.PAYMENT_FAILED_KEY,
                new PaymentFailedEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), orderId, orderNumber,
                        new BigDecimal("25140000"), "VND", "Amount exceeds the limit"));

        OrderCancelledEvent cancelledEvent = receive(cancelled, new ParameterizedTypeReference<>() {
        });
        assertThat(cancelledEvent).isNotNull();
        assertThat(cancelledEvent.orderId()).isEqualTo(orderId);
        assertThat(cancelledEvent.reason()).isEqualTo("Payment failed: Amount exceeds the limit");
        assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
    }

    @Test
    void customersOnlyReachTheirOwnOrdersAndCheckoutIsIdempotent() throws Exception {
        JsonNode first = placeOrder(customerToken(), "checkout-attempt-1");
        String orderId = first.at("/data/id").asText();

        // Same key: the first order is returned with 200 instead of a second order
        MvcResult replay = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .header("Idempotency-Key", "checkout-attempt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).at("/data/id").asText())
                .isEqualTo(orderId);

        mockMvc.perform(get("/api/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(),
                        "ROLE_CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].itemCount").value(2));
        mockMvc.perform(get("/api/orders/admin").param("keyword", first.at("/data/orderNumber").asText())
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationReason").value("Cancelled by customer: Changed my mind"));
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isUnprocessableEntity());

        // Invalid requests
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[],\"paymentMethod\":\"CARD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    private JsonNode placeOrder(String token, String idempotencyKey) throws Exception {
        var request = post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest()));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> orderRequest() {
        return Map.of(
                "items", List.of(Map.of("productId", 7, "quantity", 1), Map.of("productId", 19, "quantity", 1)),
                "shippingAddress", Map.of("recipientName", "Jane Doe", "phoneNumber", "+84901234567",
                        "addressLine1", "12 Nguyen Hue", "city", "Ho Chi Minh City", "countryCode", "VN"),
                "paymentMethod", "CARD");
    }

    private String statusOf(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus().name();
    }

    /**
     * Not auto-delete: {@code receive} with a timeout cancels its consumer, which would delete the queue.
     */
    private String probe(String routingKey) {
        Queue queue = new Queue("test.probe." + UUID.randomUUID(), false, false, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(orderExchange).with(routingKey));
        return queue.getName();
    }

    private <T> T receive(String queue, ParameterizedTypeReference<T> type) {
        return rabbitTemplate.receiveAndConvert(queue, RECEIVE_TIMEOUT_MS, type);
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

    private String customerToken() {
        return token(customerId, "ROLE_CUSTOMER");
    }

    private String adminToken() {
        return token(UUID.randomUUID(), "ROLE_ADMIN");
    }

    private String token(UUID subject, String role) {
        JwtProperties properties = new JwtProperties(SECRET, "test-issuer");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(HmacJwtSupport.secretKey(properties)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("test-issuer")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim(JwtClaimNames.EMAIL, "jane@example.com")
                .claim(JwtClaimNames.ROLES, List.of(role))
                .claim(JwtClaimNames.TOKEN_TYPE, JwtClaimNames.ACCESS_TOKEN_TYPE)
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(HmacJwtSupport.ALGORITHM).build(), claims)).getTokenValue();
    }
}
