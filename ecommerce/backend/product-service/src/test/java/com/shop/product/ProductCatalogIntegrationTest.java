package com.shop.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.ProductCreatedEvent;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtClaimNames;
import com.shop.common.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
 * Catalogue API against the real Flyway schema and sample data, plus {@code product.created} on RabbitMQ.
 * Tests only create DRAFT products, so the counts of the public (ACTIVE) seed data stay stable.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.security.jwt.secret=" + ProductCatalogIntegrationTest.SECRET,
        "app.security.jwt.issuer=test-issuer",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProductCatalogIntegrationTest {

    static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIz";
    private static final int ACTIVE_SEED_PRODUCTS = 22;

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
    private TopicExchange productExchange;

    @Test
    void publicCatalogueShowsActiveProductsOnly() throws Exception {
        JsonNode page = getJson(get("/api/products").param("size", "100"));
        assertThat(page.at("/data/totalElements").asInt()).isEqualTo(ACTIVE_SEED_PRODUCTS);
        assertThat(page.at("/data/content/0/currency").asText()).isEqualTo("VND");
        assertThat(page.at("/data/content/0/thumbnailUrl").asText()).startsWith("https://");

        mockMvc.perform(get("/api/products/23")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/slug/airpods-pro-the-he-1")).andExpect(status().isNotFound());

        // Administrators see hidden products and may filter by status
        mockMvc.perform(get("/api/products/23").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        JsonNode archived = getJson(get("/api/products").param("status", "ARCHIVED")
                .header(HttpHeaders.AUTHORIZATION, adminToken()));
        assertThat(archived.at("/data/content/0/id").asLong()).isEqualTo(24L);
        // ...while the status filter is ignored for everybody else
        JsonNode ignored = getJson(get("/api/products").param("status", "ARCHIVED"));
        assertThat(ignored.at("/data/totalElements").asInt()).isEqualTo(ACTIVE_SEED_PRODUCTS);
    }

    @Test
    void filtersByRootCategoryKeywordAndPrice() throws Exception {
        // Root category 1 (phones & tablets) includes its sub-categories 2 and 3
        assertThat(getJson(get("/api/products").param("categoryId", "1")).at("/data/totalElements").asInt())
                .isEqualTo(6);
        assertThat(getJson(get("/api/products").param("categoryId", "2")).at("/data/totalElements").asInt())
                .isEqualTo(4);
        assertThat(getJson(get("/api/products").param("keyword", "LOGITECH")).at("/data/totalElements").asInt())
                .isEqualTo(2);
        assertThat(getJson(get("/api/products").param("brand", "apple")).at("/data/totalElements").asInt())
                .isEqualTo(4);

        JsonNode expensive = getJson(get("/api/products").param("minPrice", "30000000").param("sort", "price,desc"));
        assertThat(expensive.at("/data/totalElements").asInt()).isEqualTo(3);
        assertThat(expensive.at("/data/content/0/sku").asText()).isEqualTo("LAP-ROG-G14");

        JsonNode cheapest = getJson(get("/api/products").param("sort", "price,asc").param("size", "1"));
        assertThat(cheapest.at("/data/content/0/sku").asText()).isEqualTo("ACC-UGR-C2C");

        mockMvc.perform(get("/api/products").param("sort", "description"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void batchLookupKeepsOrderAndHidesUnavailableProducts() throws Exception {
        JsonNode result = getJson(get("/api/products/by-ids").param("ids", "24,2,23,999,1"));
        assertThat(result.at("/data").size()).isEqualTo(2);
        assertThat(result.at("/data/0/id").asLong()).isEqualTo(2L);
        assertThat(result.at("/data/1/id").asLong()).isEqualTo(1L);
    }

    @Test
    void categoryTreeAndDetail() throws Exception {
        JsonNode tree = getJson(get("/api/categories"));
        assertThat(tree.at("/data").size()).isEqualTo(4);
        assertThat(tree.at("/data/0/slug").asText()).isEqualTo("dien-thoai-tablet");
        assertThat(tree.at("/data/0/children").size()).isEqualTo(2);

        mockMvc.perform(get("/api/categories/slug/laptop-gaming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(4));

        // Category 2 still contains products
        mockMvc.perform(delete("/api/categories/2").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void adminCreatesProductAndProductCreatedIsPublished() throws Exception {
        Queue probe = new AnonymousQueue();
        amqpAdmin.declareQueue(probe);
        amqpAdmin.declareBinding(BindingBuilder.bind(probe).to(productExchange).with(MessagingConstants.PRODUCT_CREATED_KEY));

        MvcResult result = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productBody("TST-HEADSET-1", "Tai nghe thử nghiệm"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("tai-nghe-thu-nghiem"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andReturn();
        long productId = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
        assertThat(productId).isGreaterThan(24L);

        ProductCreatedEvent event = rabbitTemplate.receiveAndConvert(probe.getName(), 10_000,
                new ParameterizedTypeReference<ProductCreatedEvent>() {
                });
        assertThat(event).isNotNull();
        assertThat(event.productId()).isEqualTo(productId);
        assertThat(event.sku()).isEqualTo("TST-HEADSET-1");

        // Drafts are not public; the SKU is unique
        mockMvc.perform(get("/api/products/" + productId)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productBody("TST-HEADSET-1", "Other name"))))
                .andExpect(status().isConflict());
    }

    @Test
    void writesRequireAdminAndValidInput() throws Exception {
        String body = objectMapper.writeValueAsString(productBody("TST-SEC-1", "Secured"));
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, token("ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        Map<String, Object> invalid = productBody("lower-case", "Invalid");
        invalid.put("price", -5);
        invalid.put("images", List.of(Map.of("url", "ftp://nope")));
        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations.length()").value(3));
    }

    private Map<String, Object> productBody(String sku, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sku", sku);
        body.put("name", name);
        body.put("brand", "Test");
        body.put("price", 100000);
        body.put("categoryId", 8);
        body.put("images", List.of(Map.of("url", "https://picsum.photos/seed/test/800/800", "altText", name)));
        return body;
    }

    private JsonNode getJson(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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
                .claim(JwtClaimNames.EMAIL, "someone@example.com")
                .claim(JwtClaimNames.ROLES, List.of(role))
                .claim(JwtClaimNames.TOKEN_TYPE, JwtClaimNames.ACCESS_TOKEN_TYPE)
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(HmacJwtSupport.ALGORITHM).build(), claims)).getTokenValue();
    }
}
