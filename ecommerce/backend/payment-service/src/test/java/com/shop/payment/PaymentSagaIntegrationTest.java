package com.shop.payment;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.PaymentFailedEvent;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * payment-service side of the saga against real PostgreSQL and RabbitMQ: order-service events are
 * published on the order exchange and the replies are read from the payment exchange.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.security.jwt.secret=" + PaymentSagaIntegrationTest.SECRET,
        "app.security.jwt.issuer=test-issuer",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.payment.outbox.poll-interval=200ms",
        "app.payment.simulator.max-amount=1000000",
        "app.payment.simulator.failure-rate=0.0"
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
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    @Qualifier("paymentExchange")
    private TopicExchange paymentExchange;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void chargesOnceAndRefundsWhenTheOrderIsCancelledAfterwards() throws Exception {
        String succeeded = probe(MessagingConstants.PAYMENT_SUCCEEDED_KEY);
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PaymentRequestedEvent request = paymentRequested(orderId, userId, "250000", "CARD");

        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY,
                request);
        // Redelivered request: must not produce a second charge
        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY,
                request);

        PaymentSucceededEvent event = receive(succeeded, new ParameterizedTypeReference<>() {
        });
        assertThat(event).isNotNull();
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.transactionId()).startsWith("TXN-");
        assertThat(rabbitTemplate.receive(succeeded, 2_000)).isNull();

        mockMvc.perform(get("/api/payments/orders/" + orderId).header(HttpHeaders.AUTHORIZATION,
                        token(userId, "ROLE_CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.method").value("CARD"));
        mockMvc.perform(get("/api/payments/orders/" + orderId).header(HttpHeaders.AUTHORIZATION,
                        token(UUID.randomUUID(), "ROLE_CUSTOMER")))
                .andExpect(status().isNotFound());

        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_CANCELLED_KEY,
                new OrderCancelledEvent(UUID.randomUUID(), Instant.now(), orderId, "ORD-1", userId,
                        "jane@example.com", "Cancelled by administrator"));
        waitUntil(() -> payment(orderId).getStatus() == PaymentStatus.REFUNDED);
        assertThat(payment(orderId).getRefundTransactionId()).startsWith("RFD-");
    }

    @Test
    void declinesAboveTheLimitAndNeverChargesCancelledOrders() throws Exception {
        String failed = probe(MessagingConstants.PAYMENT_FAILED_KEY);
        String succeeded = probe(MessagingConstants.PAYMENT_SUCCEEDED_KEY);

        UUID expensiveOrder = UUID.randomUUID();
        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY,
                paymentRequested(expensiveOrder, UUID.randomUUID(), "1500000", "E_WALLET"));
        PaymentFailedEvent declined = receive(failed, new ParameterizedTypeReference<>() {
        });
        assertThat(declined).isNotNull();
        assertThat(declined.orderId()).isEqualTo(expensiveOrder);
        assertThat(declined.reason()).contains("exceeds the limit");

        // The cancellation overtakes the payment request: a marker prevents the charge
        UUID cancelledOrder = UUID.randomUUID();
        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_CANCELLED_KEY,
                new OrderCancelledEvent(UUID.randomUUID(), Instant.now(), cancelledOrder, "ORD-2", UUID.randomUUID(),
                        "jane@example.com", "Out of stock"));
        waitUntil(() -> paymentRepository.findByOrderId(cancelledOrder).isPresent());
        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY,
                paymentRequested(cancelledOrder, UUID.randomUUID(), "1000", "COD"));
        assertThat(rabbitTemplate.receive(succeeded, 2_000)).isNull();
        assertThat(payment(cancelledOrder).getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        mockMvc.perform(get("/api/payments").param("status", "FAILED")
                        .header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderId").value(expensiveOrder.toString()));
        mockMvc.perform(get("/api/payments").header(HttpHeaders.AUTHORIZATION,
                        token(UUID.randomUUID(), "ROLE_CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    private static PaymentRequestedEvent paymentRequested(UUID orderId, UUID userId, String amount, String method) {
        return new PaymentRequestedEvent(UUID.randomUUID(), Instant.now(), orderId, "ORD-" + orderId.toString().substring(0, 8), userId,
                "jane@example.com", new BigDecimal(amount), "VND", method);
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
