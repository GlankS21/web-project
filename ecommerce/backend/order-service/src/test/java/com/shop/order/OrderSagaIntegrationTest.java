package com.shop.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.dto.ApiResponse;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderDeliveredEvent;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void catalogue() {
        when(productClient.getProductsByIds(anyList())).thenReturn(ApiResponse.success(List.of(
                new ProductSummary(7L, "LAP-MBA-M2", "MacBook Air M2", new BigDecimal("24990000"), "RUB",
                        "https://img/7.jpg", "ACTIVE"),
                new ProductSummary(19L, "ACC-UGR-C2C", "Кабель USB-C", new BigDecimal("150000"), "RUB", null,
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
        assertThat(paymentRequest.paymentMethod()).isEqualTo("ONLINE");
        assertThat(statusOf(orderId)).isEqualTo("AWAITING_PAYMENT");

        rabbitTemplate.convertAndSend(MessagingConstants.PAYMENT_EXCHANGE, MessagingConstants.PAYMENT_SUCCEEDED_KEY,
                new PaymentSucceededEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), orderId,
                        createdEvent.orderNumber(), "TXN-123", paymentRequest.amount(), "RUB"));
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
                        .content("{\"status\":\"SHIPPED\",\"note\":\"Трек-номер RA123456789RU\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Статус заказа обновлён"))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.statusHistory[1].reason").value("Товар зарезервирован"))
                .andExpect(jsonPath("$.data.statusHistory[2].reason").value("Оплата получена (транзакция TXN-123)"))
                .andExpect(jsonPath("$.data.statusHistory[3].reason").value("Трек-номер RA123456789RU"));
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
                        new BigDecimal("25140000"), "RUB", "Превышен лимит по карте"));

        OrderCancelledEvent cancelledEvent = receive(cancelled, new ParameterizedTypeReference<>() {
        });
        assertThat(cancelledEvent).isNotNull();
        assertThat(cancelledEvent.orderId()).isEqualTo(orderId);
        assertThat(cancelledEvent.reason()).isEqualTo("Оплата не прошла: Превышен лимит по карте");
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
                .andExpect(jsonPath("$.message").value("Заказ уже оформлен"))
                .andReturn();
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).at("/data/id").asText())
                .isEqualTo(orderId);

        mockMvc.perform(get("/api/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(),
                        "ROLE_CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Заказ не найден"));
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
                        .content("{\"reason\":\"Передумал\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Заказ отменён"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationReason").value("Отменён покупателем: Передумал"));
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Заказ " + first.at("/data/orderNumber").asText() + " в статусе «Отменён» больше нельзя отменить"));

        // Invalid requests
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[],\"paymentMethod\":\"ONLINE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // A Vietnamese phone number and an unknown country are refused in Russian
        Map<String, Object> foreignPhone = withField(orderRequest(), "shippingAddress", Map.of(
                "recipientName", "Иван Петров", "phoneNumber", "+84901234567",
                "addressLine1", "ул. Тверская, д. 7", "city", "Москва", "countryCode", "ru"));
        MvcResult invalidAddress = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(foreignPhone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();
        String invalidBody = invalidAddress.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(invalidBody)
                .contains("Укажите номер телефона в формате +7XXXXXXXXXX")
                .contains("Укажите двухбуквенный код страны ISO 3166-1, например RU");
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void couponIsRedeemedAtCheckoutAndReleasedByTheCancellation() throws Exception {
        String created = probe(MessagingConstants.ORDER_CREATED_KEY);
        String code = "pct-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode coupon = createCoupon(Map.of("code", code, "description", "Test", "discountType", "PERCENTAGE",
                "discountValue", 10, "maxDiscountAmount", 1000000, "usageLimit", 1, "active", true));
        long couponId = coupon.at("/data/id").asLong();
        String storedCode = code.toUpperCase();
        assertThat(coupon.at("/data/code").asText()).isEqualTo(storedCode);
        assertThat(coupon.at("/data/usedCount").asInt()).isZero();

        // Preview: 10 % of 25 140 000 capped at 1 000 000, free shipping judged on the subtotal
        mockMvc.perform(post("/api/orders/coupons/preview")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", " " + code + " ",
                                "items", orderRequest().get("items")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(storedCode))
                .andExpect(jsonPath("$.data.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.data.subtotal").value(25140000))
                .andExpect(jsonPath("$.data.discountAmount").value(1000000))
                .andExpect(jsonPath("$.data.shippingFee").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(24140000))
                .andExpect(jsonPath("$.data.currency").value("RUB"));

        JsonNode order = placeOrderWith(customerToken(), orderRequest(code));
        UUID orderId = UUID.fromString(order.at("/data/id").asText());
        assertThat(order.at("/data/couponCode").asText()).isEqualTo(storedCode);
        assertThat(order.at("/data/discountAmount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(order.at("/data/totalAmount").decimalValue()).isEqualByComparingTo("24140000");
        OrderCreatedEvent createdEvent = receive(created, new ParameterizedTypeReference<>() {
        });
        assertThat(createdEvent).isNotNull();
        assertThat(createdEvent.totalAmount()).isEqualByComparingTo("24140000");
        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(jsonPath("$.data.content[0].couponCode").value(storedCode))
                .andExpect(jsonPath("$.data.content[0].discountAmount").value(1000000));
        assertThat(couponOf(couponId).at("/data/usedCount").asInt()).isEqualTo(1);

        // Usage limit reached: refused at checkout and in the preview, nothing saved
        String otherCustomer = token(UUID.randomUUID(), "ROLE_CUSTOMER");
        long ordersBefore = orderRepository.count();
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, otherCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest(code))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Лимит использований промокода исчерпан"));
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        mockMvc.perform(post("/api/orders/coupons/preview")
                        .header(HttpHeaders.AUTHORIZATION, otherCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code,
                                "items", orderRequest().get("items")))))
                .andExpect(status().isUnprocessableEntity());

        // Redeemed once: cannot be deleted any more
        mockMvc.perform(delete("/api/orders/admin/coupons/" + couponId).header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isUnprocessableEntity());

        // The cancellation releases the coupon, which another customer can then use
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.couponCode").value(storedCode));
        assertThat(couponOf(couponId).at("/data/usedCount").asInt()).isZero();
        JsonNode second = placeOrderWith(otherCustomer, orderRequest(code.toUpperCase()));
        assertThat(second.at("/data/discountAmount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(couponOf(couponId).at("/data/usedCount").asInt()).isEqualTo(1);

        // Unknown and too-high minimum amounts
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest("NO-SUCH-CODE"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Промокод не найден или больше не действует"));
        String minCode = "MIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        createCoupon(Map.of("code", minCode, "discountType", "FIXED_AMOUNT", "discountValue", 500,
                "minOrderAmount", 30000000, "active", true));
        MvcResult tooSmall = mockMvc.perform(post("/api/orders/coupons/preview")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", minCode,
                                "items", orderRequest().get("items")))))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        assertThat(objectMapper.readTree(tooSmall.getResponse().getContentAsString()).at("/message").asText())
                .startsWith("Промокод действует для заказов от 30").endsWith("000 ₽");
    }

    @Test
    void administratorsManageCoupons() throws Exception {
        String code = "FIX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> fixed = new HashMap<>(Map.of("code", code, "description", "  Скидка  ",
                "discountType", "FIXED_AMOUNT", "discountValue", 500, "maxDiscountAmount", 100, "active", true));
        mockMvc.perform(post("/api/orders/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fixed)))
                .andExpect(status().isForbidden());
        JsonNode created = createCoupon(fixed);
        long couponId = created.at("/data/id").asLong();
        assertThat(created.at("/message").asText()).isEqualTo("Промокод создан");
        assertThat(created.at("/data/description").asText()).isEqualTo("Скидка");
        // The cap only applies to percentages
        assertThat(created.at("/data/maxDiscountAmount").isNumber()).isFalse();

        // Duplicate code (case-insensitive), invalid percentage and period
        mockMvc.perform(post("/api/orders/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withField(fixed, "code", code.toLowerCase()))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/orders/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "PCT-150", "discountType",
                                "PERCENTAGE", "discountValue", 150, "active", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Процент скидки должен быть от 0,01 до 100"));
        mockMvc.perform(post("/api/orders/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "PERIOD-1", "discountType",
                                "FIXED_AMOUNT", "discountValue", 100, "active", true,
                                "startsAt", "2026-02-01T00:00:00Z", "expiresAt", "2026-01-01T00:00:00Z"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(post("/api/orders/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withField(fixed, "code", "a b"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Search: keyword and active filter; the demo coupons are seeded
        mockMvc.perform(get("/api/orders/admin/coupons").param("keyword", code.substring(4).toLowerCase())
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value(code));
        mockMvc.perform(get("/api/orders/admin/coupons").param("keyword", "welcome10").param("active", "true")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(jsonPath("$.data.content[0].discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.data.content[0].maxDiscountAmount").value(5000));

        // Update, then deactivate: an inactive coupon is refused at checkout
        mockMvc.perform(put("/api/orders/admin/coupons/" + couponId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withField(fixed, "active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Промокод обновлён"))
                .andExpect(jsonPath("$.data.active").value(false));
        mockMvc.perform(get("/api/orders/admin/coupons").param("keyword", code).param("active", "true")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(post("/api/orders/coupons/preview")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code,
                                "items", orderRequest().get("items")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Промокод не найден или больше не действует"));

        // Never redeemed: can be deleted
        mockMvc.perform(delete("/api/orders/admin/coupons/" + couponId).header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Промокод удалён"));
        mockMvc.perform(get("/api/orders/admin/coupons/" + couponId).header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deliveryIsPublishedAndStatisticsAggregateThePeriod() throws Exception {
        String delivered = probe(MessagingConstants.ORDER_DELIVERED_KEY);

        // Paid order, paid at 00:30 Moscow time the day after it was created
        UUID paid = pay(placeOrder(customerToken(), null));
        // Delivered order
        UUID shipped = pay(placeOrder(customerToken(), null));
        for (String status : List.of("SHIPPED", "DELIVERED")) {
            mockMvc.perform(patch("/api/orders/admin/" + shipped + "/status")
                            .header(HttpHeaders.AUTHORIZATION, adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + status + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(status));
        }
        OrderDeliveredEvent deliveredEvent = receive(delivered, new ParameterizedTypeReference<>() {
        });
        assertThat(deliveredEvent).isNotNull();
        assertThat(deliveredEvent.orderId()).isEqualTo(shipped);
        assertThat(deliveredEvent.userId()).isEqualTo(customerId);
        assertThat(deliveredEvent.items()).extracting(item -> item.productId() + "x" + item.quantity())
                .containsExactlyInAnyOrder("7x1", "19x1");
        // Cancelled order
        UUID cancelled = UUID.fromString(placeOrder(customerToken(), null).at("/data/id").asText());
        mockMvc.perform(post("/api/orders/" + cancelled + "/cancel").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk());

        // Moved to a period of their own (Moscow is UTC+3)
        moveTo(paid, "2025-03-10T08:00:00Z", "2025-03-10T21:30:00Z");
        moveTo(shipped, "2025-03-09T20:59:00Z", "2025-03-12T09:00:00Z");
        moveTo(cancelled, "2025-03-12T10:00:00Z", null);

        mockMvc.perform(get("/api/orders/admin/stats").param("from", "2025-03-09").param("to", "2025-03-12")
                        .param("top", "1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2025-03-09"))
                .andExpect(jsonPath("$.data.to").value("2025-03-12"))
                .andExpect(jsonPath("$.data.currency").value("RUB"))
                .andExpect(jsonPath("$.data.orderCount").value(3))
                .andExpect(jsonPath("$.data.paidOrderCount").value(2))
                .andExpect(jsonPath("$.data.cancelledOrderCount").value(1))
                .andExpect(jsonPath("$.data.revenue").value(50280000))
                .andExpect(jsonPath("$.data.averageOrderValue").value(25140000))
                .andExpect(jsonPath("$.data.ordersByStatus.PAID").value(1))
                .andExpect(jsonPath("$.data.ordersByStatus.DELIVERED").value(1))
                .andExpect(jsonPath("$.data.ordersByStatus.CANCELLED").value(1))
                .andExpect(jsonPath("$.data.ordersByStatus.PENDING").value(0))
                .andExpect(jsonPath("$.data.daily.length()").value(4))
                .andExpect(jsonPath("$.data.daily[0].date").value("2025-03-09"))
                .andExpect(jsonPath("$.data.daily[0].orders").value(1))
                .andExpect(jsonPath("$.data.daily[0].paidOrders").value(0))
                .andExpect(jsonPath("$.data.daily[1].orders").value(1))
                .andExpect(jsonPath("$.data.daily[1].revenue").value(0))
                .andExpect(jsonPath("$.data.daily[2].orders").value(0))
                .andExpect(jsonPath("$.data.daily[2].paidOrders").value(1))
                .andExpect(jsonPath("$.data.daily[2].revenue").value(25140000))
                .andExpect(jsonPath("$.data.daily[3].orders").value(1))
                .andExpect(jsonPath("$.data.daily[3].paidOrders").value(1))
                .andExpect(jsonPath("$.data.topProducts.length()").value(1))
                .andExpect(jsonPath("$.data.topProducts[0].productId").value(7))
                .andExpect(jsonPath("$.data.topProducts[0].productName").value("MacBook Air M2"))
                .andExpect(jsonPath("$.data.topProducts[0].sku").value("LAP-MBA-M2"))
                .andExpect(jsonPath("$.data.topProducts[0].quantity").value(2))
                .andExpect(jsonPath("$.data.topProducts[0].revenue").value(49980000));

        mockMvc.perform(get("/api/orders/admin/stats").param("from", "2024-01-01").param("to", "2025-03-12")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(get("/api/orders/admin/stats").param("top", "21")
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/orders/admin/stats").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/admin/stats").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily.length()").value(30));
    }

    /**
     * Drives a new order through the saga until it is PAID.
     */
    private UUID pay(JsonNode order) throws Exception {
        UUID orderId = UUID.fromString(order.at("/data/id").asText());
        String orderNumber = order.at("/data/orderNumber").asText();
        rabbitTemplate.convertAndSend(MessagingConstants.INVENTORY_EXCHANGE, MessagingConstants.STOCK_RESERVED_KEY,
                new StockReservedEvent(UUID.randomUUID(), Instant.now(), orderId, orderNumber));
        waitUntil(() -> "AWAITING_PAYMENT".equals(statusOf(orderId)));
        rabbitTemplate.convertAndSend(MessagingConstants.PAYMENT_EXCHANGE, MessagingConstants.PAYMENT_SUCCEEDED_KEY,
                new PaymentSucceededEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), orderId, orderNumber,
                        "TXN-" + orderNumber, order.at("/data/totalAmount").decimalValue(), "RUB"));
        waitUntil(() -> "PAID".equals(statusOf(orderId)));
        return orderId;
    }

    private void moveTo(UUID orderId, String createdAt, String paidAt) {
        jdbcTemplate.update("update orders set created_at = ?, paid_at = ? where id = ?",
                Timestamp.from(Instant.parse(createdAt)), paidAt == null ? null : Timestamp.from(Instant.parse(paidAt)),
                orderId);
    }

    private JsonNode createCoupon(Map<String, Object> coupon) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(coupon)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode couponOf(long couponId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders/admin/coupons/" + couponId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static Map<String, Object> withField(Map<String, Object> source, String field, Object value) {
        Map<String, Object> copy = new HashMap<>(source);
        copy.put(field, value);
        return copy;
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

    private JsonNode placeOrderWith(String token, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> orderRequest(String couponCode) {
        return withField(orderRequest(), "couponCode", couponCode);
    }

    private Map<String, Object> orderRequest() {
        return Map.of(
                "items", List.of(Map.of("productId", 7, "quantity", 1), Map.of("productId", 19, "quantity", 1)),
                "shippingAddress", Map.of("recipientName", "Иван Петров", "phoneNumber", "+79161234567",
                        "addressLine1", "ул. Тверская, д. 7", "city", "Москва", "postalCode", "125009",
                        "countryCode", "RU"),
                "paymentMethod", "ONLINE");
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
