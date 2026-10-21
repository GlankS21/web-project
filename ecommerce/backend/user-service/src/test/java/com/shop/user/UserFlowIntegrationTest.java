package com.shop.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtClaimNames;
import com.shop.common.security.JwtProperties;
import com.shop.user.repository.UserProfileRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * user.registered consumption and the address book API against real PostgreSQL and RabbitMQ.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.security.jwt.secret=" + UserFlowIntegrationTest.SECRET,
        "app.security.jwt.issuer=test-issuer",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.placeholders.[admin_email]=admin@shop.local"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class UserFlowIntegrationTest {

    static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIz";

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
    private UserProfileRepository userProfileRepository;

    @Test
    void registrationEventCreatesProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        rabbitTemplate.convertAndSend(MessagingConstants.USER_EXCHANGE, MessagingConstants.USER_REGISTERED_KEY,
                new UserRegisteredEvent(UUID.randomUUID(), Instant.now(), userId, "event@example.com", "Eve", "Nt"));

        waitUntil(() -> userProfileRepository.existsById(userId));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(userId, "event@example.com", "ROLE_CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Eve"))
                .andExpect(jsonPath("$.data.email").value("event@example.com"));
    }

    @Test
    void addressBookKeepsExactlyOneDefault() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = bearer(userId, "book@example.com", "ROLE_CUSTOMER");

        String first = createAddress(token, "First", false).at("/data/id").asText();
        JsonNode second = createAddress(token, "Second", true);
        assertThat(second.at("/data/defaultAddress").asBoolean()).isTrue();

        JsonNode list = getJson("/api/users/me/addresses", token);
        assertThat(list.at("/data").size()).isEqualTo(2);
        assertThat(list.at("/data/0/recipientName").asText()).isEqualTo("Second");
        assertThat(list.at("/data/1/defaultAddress").asBoolean()).isFalse();

        mockMvc.perform(delete("/api/users/me/addresses/" + second.at("/data/id").asText())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        JsonNode afterDelete = getJson("/api/users/me/addresses", token);
        assertThat(afterDelete.at("/data").size()).isEqualTo(1);
        assertThat(afterDelete.at("/data/0/id").asText()).isEqualTo(first);
        assertThat(afterDelete.at("/data/0/defaultAddress").asBoolean()).isTrue();

        // Another user cannot see this address
        mockMvc.perform(get("/api/users/me/addresses/" + first)
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "other@example.com", "ROLE_CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidAddressIsRejectedWithViolations() throws Exception {
        String token = bearer(UUID.randomUUID(), "invalid@example.com", "ROLE_CUSTOMER");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientName", "");
        body.put("phoneNumber", "abc");
        body.put("addressLine1", "Street");
        body.put("city", "City");
        body.put("countryCode", "vietnam");

        mockMvc.perform(post("/api/users/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations.length()").value(3));
    }

    @Test
    void adminListingRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION,
                        bearer(UUID.randomUUID(), "customer@example.com", "ROLE_CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION,
                        bearer(UUID.randomUUID(), "admin@example.com", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode createAddress(String token, String recipient, boolean makeDefault) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientName", recipient);
        body.put("phoneNumber", "+84901234567");
        body.put("addressLine1", "12 Nguyen Hue");
        body.put("city", "Ho Chi Minh City");
        body.put("countryCode", "VN");
        body.put("defaultAddress", makeDefault);
        MvcResult result = mockMvc.perform(post("/api/users/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(UUID userId, String email, String role) {
        JwtProperties properties = new JwtProperties(SECRET, "test-issuer");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(HmacJwtSupport.secretKey(properties)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("test-issuer")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim(JwtClaimNames.EMAIL, email)
                .claim(JwtClaimNames.ROLES, List.of(role))
                .claim(JwtClaimNames.TOKEN_TYPE, JwtClaimNames.ACCESS_TOKEN_TYPE)
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(HmacJwtSupport.ALGORITHM).build(), claims)).getTokenValue();
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within 15 seconds");
            }
            Thread.sleep(200);
        }
    }
}
