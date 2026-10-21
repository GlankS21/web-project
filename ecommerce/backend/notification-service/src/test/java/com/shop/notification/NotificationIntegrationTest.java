package com.shop.notification;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtClaimNames;
import com.shop.common.security.JwtProperties;
import com.shop.notification.entity.Notification;
import com.shop.notification.entity.NotificationStatus;
import com.shop.notification.repository.NotificationRepository;
import com.shop.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Events consumed from RabbitMQ become e-mails recorded in PostgreSQL; the SMTP server is mocked.
 * The retry job is slowed down so that the tests drive retries themselves.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.security.jwt.secret=" + NotificationIntegrationTest.SECRET,
        "app.security.jwt.issuer=test-issuer",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.notification.shop-name=TechShop",
        "app.notification.retry.max-attempts=2",
        "app.notification.retry.interval=1h"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NotificationIntegrationTest {

    static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIz";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.0-management-alpine");

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationService notificationService;

    @Test
    void sendsOneWelcomeEmailPerRegistration() throws Exception {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = new UserRegisteredEvent(UUID.randomUUID(), Instant.now(), userId,
                "jane@example.com", "Jane", "Doe");

        rabbitTemplate.convertAndSend(MessagingConstants.USER_EXCHANGE, MessagingConstants.USER_REGISTERED_KEY, event);
        rabbitTemplate.convertAndSend(MessagingConstants.USER_EXCHANGE, MessagingConstants.USER_REGISTERED_KEY, event);

        Notification notification = await(() -> notificationRepository.findAll().stream()
                .filter(n -> userId.equals(n.getUserId()) && n.getStatus() == NotificationStatus.SENT).findFirst());
        assertThat(notification.getSubject()).isEqualTo("[TechShop] Chào mừng bạn đến với TechShop");
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("jane@example.com");
        assertThat(mail.getValue().getFrom()).isEqualTo("no-reply@shop.local");

        Thread.sleep(1_000);
        assertThat(notificationRepository.findAll()).filteredOn(n -> userId.equals(n.getUserId())).hasSize(1);

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, token(userId, "ROLE_CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("WELCOME"));
        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION,
                        token(UUID.randomUUID(), "ROLE_CUSTOMER")))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void failedDeliveryIsRetriedThenResentByAnAdministrator() throws Exception {
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(SimpleMailMessage.class));
        UUID userId = UUID.randomUUID();

        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EXCHANGE, MessagingConstants.ORDER_CONFIRMED_KEY,
                new OrderConfirmedEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "ORD-20261021-ABCDEF",
                        userId, "jane@example.com", new BigDecimal("25140000"), "VND"));

        Notification failed = await(() -> notificationRepository.findAll().stream()
                .filter(n -> userId.equals(n.getUserId()) && n.getStatus() == NotificationStatus.FAILED).findFirst());
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("Connection refused");
        assertThat(failed.getBody()).contains("Thanh toán 25.140.000 VND cho đơn hàng ORD-20261021-ABCDEF");

        // Second and last automatic attempt still fails; a third run does not try again
        notificationService.retryUndelivered();
        notificationService.retryUndelivered();
        assertThat(notificationRepository.findById(failed.getId()).orElseThrow().getAttempts()).isEqualTo(2);

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        mockMvc.perform(post("/api/notifications/admin/" + failed.getId() + "/resend")
                        .header(HttpHeaders.AUTHORIZATION, token(userId, "ROLE_CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/notifications/admin/" + failed.getId() + "/resend")
                        .header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.attempts").value(3))
                .andExpect(jsonPath("$.data.lastError").doesNotExist());
        mockMvc.perform(get("/api/notifications/admin").param("type", "ORDER_CONFIRMED")
                        .header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reference").value("ORD-20261021-ABCDEF"));
        mockMvc.perform(post("/api/notifications/admin/" + UUID.randomUUID() + "/resend")
                        .header(HttpHeaders.AUTHORIZATION, token(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isNotFound());
    }

    private static <T> T await(Supplier<Optional<T>> probe) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        Optional<T> value;
        while ((value = probe.get()).isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within 15 seconds");
            }
            Thread.sleep(200);
        }
        return value.get();
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
