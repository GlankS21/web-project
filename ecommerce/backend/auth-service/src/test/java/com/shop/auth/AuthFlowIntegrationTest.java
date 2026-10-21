package com.shop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.constant.MessagingConstants;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end flow through the HTTP API with real PostgreSQL and RabbitMQ:
 * register, authenticated call, refresh rotation, reuse detection and role enforcement.
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
        postJson("/api/auth/refresh", Map.of("refreshToken", firstRefresh), 401);
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
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        JsonNode body = postJson("/api/auth/login",
                Map.of("email", "admin@shop.local", "password", "WrongPass1"), 401);

        assertThat(body.at("/message").asText()).isEqualTo("Invalid email or password");
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
