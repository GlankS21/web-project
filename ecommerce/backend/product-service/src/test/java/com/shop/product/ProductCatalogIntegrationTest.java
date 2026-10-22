package com.shop.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.ProductCreatedEvent;
import com.shop.common.security.HmacJwtSupport;
import com.shop.common.security.JwtClaimNames;
import com.shop.common.security.JwtProperties;
import com.shop.product.service.CategoryService;
import com.shop.product.service.ProductService;
import com.shop.product.util.SlugUtils;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.regex.Pattern;

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
    /** Letters found in Vietnamese but not in Russian or plain ASCII text (written as escapes on purpose). */
    private static final Pattern VIETNAMESE_LETTERS = Pattern.compile(
            "[\\u0102\\u0103\\u00C2\\u00E2\\u0110\\u0111\\u00CA\\u00EA\\u00D4\\u00F4\\u01A0\\u01A1\\u01AF\\u01B0"
                    + "\\u00C0-\\u00C3\\u00C8-\\u00CA\\u00CC\\u00CD\\u00D2-\\u00D5\\u00D9\\u00DA\\u00DD"
                    + "\\u00E0-\\u00E3\\u00E8-\\u00EA\\u00EC\\u00ED\\u00F2-\\u00F5\\u00F9\\u00FA\\u00FD"
                    + "\\u0128\\u0129\\u0168\\u0169\\u1EA0-\\u1EF9]");
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");

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
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void publicCatalogueShowsActiveProductsOnly() throws Exception {
        JsonNode page = getJson(get("/api/products").param("size", "100"));
        assertThat(page.at("/data/totalElements").asInt()).isEqualTo(ACTIVE_SEED_PRODUCTS);
        assertThat(page.at("/data/content/0/currency").asText()).isEqualTo("RUB");
        assertThat(page.at("/data/content/0/thumbnailUrl").asText()).startsWith("https://");

        mockMvc.perform(get("/api/products/23")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/slug/naushniki-apple-airpods-pro-1-go-pokoleniia"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Товар не найден"));

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

        JsonNode expensive = getJson(get("/api/products").param("minPrice", "100000").param("sort", "price,desc"));
        assertThat(expensive.at("/data/totalElements").asInt()).isEqualTo(3);
        assertThat(expensive.at("/data/content/0/sku").asText()).isEqualTo("LAP-ROG-G14");

        JsonNode cheapest = getJson(get("/api/products").param("sort", "price,asc").param("size", "1"));
        assertThat(cheapest.at("/data/content/0/sku").asText()).isEqualTo("ACC-UGR-C2C");

        mockMvc.perform(get("/api/products").param("sort", "description"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "Товары можно сортировать только по полям: createdAt, name, price, ratingAverage, sku, updatedAt"));
        mockMvc.perform(get("/api/products").param("minPrice", "500").param("maxPrice", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Минимальная цена не может быть больше максимальной"));
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
        assertThat(tree.at("/data/0/slug").asText()).isEqualTo("telefony-i-planshety");
        assertThat(tree.at("/data/0/name").asText()).isEqualTo("Телефоны и планшеты");
        assertThat(tree.at("/data/0/children").size()).isEqualTo(2);

        mockMvc.perform(get("/api/categories/slug/igrovye-noutbuki"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Игровые ноутбуки"))
                .andExpect(jsonPath("$.data.parentId").value(4));

        // Category 2 still contains products
        mockMvc.perform(delete("/api/categories/2").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "В категории есть товары: сначала перенесите их в другую категорию"));
        mockMvc.perform(get("/api/categories/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Категория не найдена"));
    }

    @Test
    void seededCatalogueIsInRussianAfterMigration() throws Exception {
        mockMvc.perform(get("/api/categories/slug/smartfony"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.name").value("Смартфоны"));
        mockMvc.perform(get("/api/products/slug/smartfon-apple-iphone-15-128-gb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.sku").value("PHN-IP15-128"))
                .andExpect(jsonPath("$.data.name").value("Смартфон Apple iPhone 15 128 ГБ"))
                .andExpect(jsonPath("$.data.shortDescription").value("Чип A16 Bionic, камера 48 Мп, Dynamic Island"))
                .andExpect(jsonPath("$.data.category.name").value("Смартфоны"))
                .andExpect(jsonPath("$.data.images[0].altText").value("Смартфон Apple iPhone 15 128 ГБ"));
        mockMvc.perform(get("/api/products/slug/iphone-15-128gb")).andExpect(status().isNotFound());

        List<Map<String, Object>> categories = jdbcTemplate.queryForList(
                "select id, name, slug, description from categories where id <= 12");
        assertThat(categories).hasSize(12).allSatisfy(row -> {
            assertThat(row.get("name").toString()).containsPattern(CYRILLIC);
            assertThat(row.get("slug")).isEqualTo(
                    SlugUtils.slugify(row.get("name").toString(), CategoryService.SLUG_MAX_LENGTH));
        });
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
                "select id, name, slug, short_description, description from products where id <= 24");
        assertThat(products).hasSize(24).allSatisfy(row -> {
            assertThat(row.get("name").toString()).containsPattern(CYRILLIC);
            assertThat(row.get("short_description").toString()).containsPattern(CYRILLIC);
            assertThat(row.get("slug")).isEqualTo(
                    SlugUtils.slugify(row.get("name").toString(), ProductService.SLUG_MAX_LENGTH));
        });
        List<String> texts = jdbcTemplate.queryForList(
                "select name || ' ' || coalesce(description, '') from categories "
                        + "union all select name || ' ' || coalesce(short_description, '') || ' ' "
                        + "|| coalesce(description, '') from products "
                        + "union all select coalesce(alt_text, '') from product_images", String.class);
        assertThat(texts).isNotEmpty().noneMatch(text -> VIETNAMESE_LETTERS.matcher(text).find());
    }

    @Test
    void adminCreatesProductAndProductCreatedIsPublished() throws Exception {
        Queue probe = new AnonymousQueue();
        amqpAdmin.declareQueue(probe);
        amqpAdmin.declareBinding(BindingBuilder.bind(probe).to(productExchange).with(MessagingConstants.PRODUCT_CREATED_KEY));

        MvcResult result = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productBody("TST-HEADSET-1", "Тестовые наушники"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("testovye-naushniki"))
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
                        .content(objectMapper.writeValueAsString(productBody("TST-HEADSET-1", "Другие наушники"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Товар с артикулом «TST-HEADSET-1» уже существует"));
    }

    @Test
    void writesRequireAdminAndValidInput() throws Exception {
        String body = objectMapper.writeValueAsString(productBody("TST-SEC-1", "Защищённый товар"));
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, token("ROLE_CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        Map<String, Object> invalid = productBody("lower-case", "Неверный товар");
        invalid.put("price", -5);
        invalid.put("images", List.of(Map.of("url", "ftp://nope")));
        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Проверьте правильность заполнения полей"))
                .andExpect(jsonPath("$.violations.length()").value(3))
                .andExpect(jsonPath("$.violations[?(@.field == 'sku')].message").value(
                        "Артикул должен состоять из 3–64 заглавных латинских букв, цифр или дефисов"))
                .andExpect(jsonPath("$.violations[?(@.field == 'images[0].url')].message").value(
                        "Укажите адрес, начинающийся с http:// или https://, или адрес загруженного изображения"))
                .andExpect(jsonPath("$.violations[?(@.field == 'price')].message").value(
                        "должно быть больше, чем 0"));
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
