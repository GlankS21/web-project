package com.shop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.PasswordResetRequestedEvent;
import com.shop.common.event.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end flow through the HTTP API with real PostgreSQL and RabbitMQ:
 * register, authenticated call, refresh rotation, reuse detection, role enforcement and password reset.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.scheduling.enabled=false",
        "app.security.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIz",
        "app.security.jwt.issuer=test-issuer",
        "app.auth.access-token-ttl=15m",
        "app.auth.refresh-token-ttl=7d",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.[admin_email]=admin@shop.local",
        "spring.flyway.placeholders.[admin_password]=AdminPass123"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowIntegrationTest {

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
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private TopicExchange userExchange;

    @Test
    void registrationRefreshRotationAndReuseDetection() throws Exception {
        Queue probe = new AnonymousQueue();
        rabbitAdmin.declareQueue(probe);
        rabbitAdmin.declareBinding(BindingBuilder.bind(probe).to(userExchange)
                .with(MessagingConstants.USER_REGISTERED_KEY));

        JsonNode registered = postJson("/api/auth/register", Map.of(
                "email", "Flow.User@Example.com",
                "password", "Secret123",
                "firstName", "Flow",
                "lastName", "User"), 201);
        String accessToken = registered.at("/data/accessToken").asText();
        String firstRefresh = registered.at("/data/refreshToken").asText();
        assertThat(registered.at("/data/account/email").asText()).isEqualTo("flow.user@example.com");
        assertThat(registered.at("/message").asText()).isEqualTo("Аккаунт создан");

        UserRegisteredEvent event = rabbitTemplate.receiveAndConvert(probe.getName(), 10_000,
                new ParameterizedTypeReference<UserRegisteredEvent>() {
                });
        assertThat(event).isNotNull();
        assertThat(event.email()).isEqualTo("flow.user@example.com");
        assertThat(event.firstName()).isEqualTo("Flow");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_CUSTOMER"));

        JsonNode rotated = postJson("/api/auth/refresh", Map.of("refreshToken", firstRefresh), 200);
        String secondRefresh = rotated.at("/data/refreshToken").asText();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // Replaying the rotated token is treated as theft: it fails and kills the whole token family.
        assertThat(postJson("/api/auth/refresh", Map.of("refreshToken", firstRefresh), 401)
                .at("/message").asText()).isEqualTo("Сеанс был завершён, войдите снова");
        postJson("/api/auth/refresh", Map.of("refreshToken", secondRefresh), 401);
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        String adminToken = postJson("/api/auth/login",
                Map.of("email", "ADMIN@shop.local", "password", "AdminPass123"), 200)
                .at("/data/accessToken").asText();
        String customerToken = postJson("/api/auth/register", Map.of(
                "email", "customer.roles@example.com",
                "password", "Secret123",
                "firstName", "Cus",
                "lastName", "Tomer"), 201)
                .at("/data/accessToken").asText();

        mockMvc.perform(get("/api/auth/admin/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").isNumber());
        mockMvc.perform(get("/api/auth/admin/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/auth/admin/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidRegistrationReturnsFieldViolations() throws Exception {
        JsonNode body = postJson("/api/auth/register", Map.of(
                "email", "not-an-email",
                "password", "short",
                "firstName", "",
                "lastName", "X"), 400);

        assertThat(body.at("/code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.at("/violations").size()).isGreaterThanOrEqualTo(3);
        assertThat(body.at("/message").asText()).isEqualTo("Проверьте правильность заполнения полей");
    }

    @Test
    void passwordPolicyViolationIsExplainedInRussian() throws Exception {
        JsonNode body = postJson("/api/auth/register", Map.of(
                "email", "ivan.petrov@example.ru",
                "password", "onlyletters",
                "firstName", "Иван",
                "lastName", "Петров"), 400);

        assertThat(body.at("/violations/0/field").asText()).isEqualTo("password");
        assertThat(body.at("/violations/0/message").asText())
                .isEqualTo("Пароль должен содержать хотя бы одну букву и одну цифру");
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        JsonNode body = postJson("/api/auth/login",
                Map.of("email", "admin@shop.local", "password", "WrongPass1"), 401);

        assertThat(body.at("/message").asText()).isEqualTo("Неверный e-mail или пароль");
    }

    @Test
    void forgottenPasswordIsResetWithTheEmailedToken() throws Exception {
        // Exclusive but not auto-delete: the probe is consumed twice
        Queue probe = new Queue("auth-test.password-reset." + UUID.randomUUID(), false, true, false);
        rabbitAdmin.declareQueue(probe);
        rabbitAdmin.declareBinding(BindingBuilder.bind(probe).to(userExchange)
                .with(MessagingConstants.USER_PASSWORD_RESET_REQUESTED_KEY));
        String refreshToken = postJson("/api/auth/register", Map.of(
                "email", "forgetful@example.com",
                "password", "Secret123",
                "firstName", "For",
                "lastName", "Getful"), 201)
                .at("/data/refreshToken").asText();

        // Unknown and known e-mails get the same answer; only the known one produces an e-mail.
        String unknownMessage = postJson("/api/auth/password/forgot",
                Map.of("email", "nobody@example.com"), 200).at("/message").asText();
        String knownMessage = postJson("/api/auth/password/forgot",
                Map.of("email", "Forgetful@Example.com"), 200).at("/message").asText();
        assertThat(knownMessage).isEqualTo(unknownMessage)
                .isEqualTo("Если аккаунт с таким e-mail существует, мы отправили на него ссылку для сброса пароля");

        PasswordResetRequestedEvent event = rabbitTemplate.receiveAndConvert(probe.getName(), 10_000,
                new ParameterizedTypeReference<PasswordResetRequestedEvent>() {
                });
        assertThat(event).isNotNull();
        assertThat(event.email()).isEqualTo("forgetful@example.com");
        assertThat(event.expiresAt()).isAfter(event.occurredAt());
        String resetToken = event.token();

        // A second request within the cooldown is answered the same way but sends nothing.
        postJson("/api/auth/password/forgot", Map.of("email", "forgetful@example.com"), 200);
        assertThat(rabbitTemplate.receive(probe.getName(), 1_000)).isNull();

        postJson("/api/auth/password/reset", Map.of("token", resetToken, "newPassword", "short"), 400);
        JsonNode reset = postJson("/api/auth/password/reset",
                Map.of("token", resetToken, "newPassword", "NewSecret456"), 200);
        assertThat(reset.at("/message").asText()).isEqualTo("Пароль изменён, войдите с новым паролем");

        postJson("/api/auth/login", Map.of("email", "forgetful@example.com", "password", "Secret123"), 401);
        postJson("/api/auth/login", Map.of("email", "forgetful@example.com", "password", "NewSecret456"), 200);
        // Sessions opened with the old password are revoked.
        postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken), 401);

        JsonNode reused = postJson("/api/auth/password/reset",
                Map.of("token", resetToken, "newPassword", "Another789"), 400);
        assertThat(reused.at("/code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(reused.at("/message").asText())
                .isEqualTo("Ссылка для сброса пароля недействительна или устарела. Запросите новую");
        postJson("/api/auth/password/reset", Map.of("token", "not-a-token", "newPassword", "Another789"), 400);
    }

    private JsonNode postJson(String path, Map<String, String> body, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
