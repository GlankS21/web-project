package com.shop.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.PaymentRefundedEvent;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.common.event.PaymentSucceededEvent;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtClaimNames;
import com.shop.common.security.JwtProperties;
import com.shop.payment.entity.Payment;
import com.shop.payment.entity.PaymentStatus;
import com.shop.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * payment-service side of the saga against real PostgreSQL and RabbitMQ, with the mock provider:
 * order-service events are published on the order exchange and the replies are read from the payment
 * exchange; the customer pays with YooKassa test cards in the mock card form.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.security.jwt.secret=" + PaymentSagaIntegrationTest.SECRET,
        "app.security.jwt.issuer=test-issuer",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.payment.outbox.poll-interval=200ms",
        "app.payment.provider=mock"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PaymentSagaIntegrationTest {

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
    @Qualifier("paymentExchange")
    private TopicExchange paymentExchange;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void declinedCardIsReplacedByAnotherAndRefundedAfterCancellation() throws Exception {
        String succeeded = probe(MessagingConstants.PAYMENT_SUCCEEDED_KEY);
        String refunded = probe(MessagingConstants.PAYMENT_REFUNDED_KEY);
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String customer = token(userId, "ROLE_CUSTOMER");
        PaymentRequestedEvent request = paymentRequested(orderId, userId, "25990.00", "ONLINE");

        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY,
                request);
        // Redelivered request: must not create a second payment
        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY,
                request);
        waitUntil(() -> paymentRepository.findByOrderId(orderId).map(Payment::getConfirmationToken).isPresent());

        JsonNode pending = paymentOf(orderId, customer);
        assertThat(pending.at("/status").asText()).isEqualTo("PENDING");
        assertThat(pending.at("/attempts").asInt()).isEqualTo(1);
        assertThat(pending.at("/confirmationToken").asText()).startsWith("mock-ct-");
        String firstPayment = currentChargeId(pending);

        // Somebody else cannot pay with the token of the order
        pay(firstPayment, "5555555555554444", null, token(UUID.randomUUID(), "ROLE_CUSTOMER"))
                .andExpect(status().isNotFound());
        // Malformed card data never reaches the gateway
        pay(firstPayment, "1234", null, customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Проверьте правильность заполнения полей"))
                .andExpect(jsonPath("$.violations[0].field").value("cardNumber"))
                .andExpect(jsonPath("$.violations[0].message").value("Номер карты должен состоять из 12–19 цифр"));

        pay(firstPayment, "5555555555554600", null, customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threeDSecureRequired").value(false))
                .andExpect(jsonPath("$.data.payment.status").value("FAILED"))
                .andExpect(jsonPath("$.data.payment.failureReason").value("insufficient_funds"))
                .andExpect(jsonPath("$.data.payment.failureMessage").value("Недостаточно средств для оплаты"))
                .andExpect(jsonPath("$.data.payment.transactions[0].failureMessage")
                        .value("Недостаточно средств для оплаты"));
        assertThat(rabbitTemplate.receive(succeeded, 1_000)).isNull();

        // Another card, same order
        JsonNode retried = objectMapper.readTree(mockMvc.perform(post("/api/payments/orders/" + orderId + "/retry")
                        .header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.attempts").value(2))
                .andReturn().getResponse().getContentAsString()).get("data");
        String secondPayment = currentChargeId(retried);
        assertThat(secondPayment).isNotEqualTo(firstPayment);

        // 3-D Secure card: the code is asked first
        pay(secondPayment, "5555555555554477", null, customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threeDSecureRequired").value(true))
                .andExpect(jsonPath("$.data.payment.status").value("PENDING"));
        pay(secondPayment, "5555555555554477", "123", customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.payment.transactions.length()").value(2));
        PaymentSucceededEvent captured = receive(succeeded, new ParameterizedTypeReference<>() {
        });
        assertThat(captured).isNotNull();
        assertThat(captured.orderId()).isEqualTo(orderId);
        assertThat(captured.transactionId()).isEqualTo(secondPayment);
        assertThat(captured.amount()).isEqualByComparingTo("25990.00");

        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_CANCELLED_KEY,
                new OrderCancelledEvent(UUID.randomUUID(), Instant.now(), orderId, request.orderNumber(), userId,
                        "jane@example.com", "Cancelled by administrator"));
        PaymentRefundedEvent refund = receive(refunded, new ParameterizedTypeReference<>() {
        });
        assertThat(refund).isNotNull();
        assertThat(refund.refundId()).startsWith("mock-refund-");
        JsonNode done = paymentOf(orderId, customer);
        assertThat(done.at("/status").asText()).isEqualTo("REFUNDED");
        assertThat(done.at("/transactions").size()).isEqualTo(3);
        assertThat(done.at("/transactions/2/type").asText()).isEqualTo("REFUND");
    }

    @Test
    void cashOnDeliveryIsAcceptedAndCancelledOrdersAreNeverCharged() throws Exception {
        String succeeded = probe(MessagingConstants.PAYMENT_SUCCEEDED_KEY);

        UUID codOrder = UUID.randomUUID();
        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY,
                paymentRequested(codOrder, UUID.randomUUID(), "1290.00", "COD"));
        PaymentSucceededEvent accepted = receive(succeeded, new ParameterizedTypeReference<>() {
        });
        assertThat(accepted).isNotNull();
        assertThat(accepted.orderId()).isEqualTo(codOrder);

        // The cancellation overtakes the payment request: a marker prevents the charge
        UUID cancelledOrder = UUID.randomUUID();
        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_CANCELLED_KEY,
                new OrderCancelledEvent(UUID.randomUUID(), Instant.now(), cancelledOrder, "ORD-2", UUID.randomUUID(),
                        "jane@example.com", "Out of stock"));
        waitUntil(() -> paymentRepository.findByOrderId(cancelledOrder).isPresent());
        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY,
                paymentRequested(cancelledOrder, UUID.randomUUID(), "1000", "ONLINE"));
        assertThat(rabbitTemplate.receive(succeeded, 2_000)).isNull();
        assertThat(payment(cancelledOrder).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment(cancelledOrder).getAttempts()).isZero();

        mockMvc.perform(get("/api/payments").param("status", "CANCELLED")
                        .header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderId").value(cancelledOrder.toString()));
        mockMvc.perform(get("/api/payments").header(HttpHeaders.AUTHORIZATION,
                        token(UUID.randomUUID(), "ROLE_CUSTOMER")))
                .andExpect(status().isForbidden());
        // Errors are answered in Russian
        mockMvc.perform(get("/api/payments/orders/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION,
                        token(UUID.randomUUID(), "ROLE_CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Платёж не найден"));
    }

    private org.springframework.test.web.servlet.ResultActions pay(String mockPaymentId, String card,
                                                                  String threeDSecureCode, String authorization)
            throws Exception {
        String code = threeDSecureCode == null ? "" : ",\"threeDSecureCode\":\"" + threeDSecureCode + "\"";
        return mockMvc.perform(post("/api/payments/mock/" + mockPaymentId + "/pay")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardNumber\":\"" + card + "\",\"expiry\":\"12/30\",\"cvc\":\"123\"" + code + "}"));
    }

    private JsonNode paymentOf(UUID orderId, String authorization) throws Exception {
        String body = mockMvc.perform(get("/api/payments/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    /** Provider id of the latest charge attempt, i.e. the mock payment to pay. */
    private static String currentChargeId(JsonNode payment) {
        JsonNode transactions = payment.get("transactions");
        return transactions.get(transactions.size() - 1).get("providerId").asText();
    }

    private static PaymentRequestedEvent paymentRequested(UUID orderId, UUID userId, String amount, String method) {
        return new PaymentRequestedEvent(UUID.randomUUID(), Instant.now(), orderId,
                "ORD-" + orderId.toString().substring(0, 8), userId, "jane@example.com", new BigDecimal(amount),
                "RUB", method);
    }

    private Payment payment(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow();
    }

    /**
     * Not auto-delete: {@code receive} with a timeout cancels its consumer, which would delete the queue.
     */
    private String probe(String routingKey) {
        Queue queue = new Queue("test.probe." + UUID.randomUUID(), false, false, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(paymentExchange).with(routingKey));
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

    static String token(UUID subject, String role) {
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
