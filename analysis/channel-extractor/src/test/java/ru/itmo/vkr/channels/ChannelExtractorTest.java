package ru.itmo.vkr.channels;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itmo.vkr.channels.model.Channel;
import ru.itmo.vkr.channels.model.ChannelModel;
import ru.itmo.vkr.channels.model.Endpoint;
import ru.itmo.vkr.channels.model.HttpClientMethod;
import ru.itmo.vkr.channels.model.Listener;
import ru.itmo.vkr.channels.model.Param;
import ru.itmo.vkr.channels.model.Publisher;
import ru.itmo.vkr.channels.model.Unresolved;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Runs the extractor on a two-service system written to a temporary directory: an order service
 * calling a catalog service over Feign and publishing order events, directly and through an
 * outbox, that the catalog service consumes.
 */
class ChannelExtractorTest {

    @TempDir
    static Path root;

    static ChannelModel model;

    @BeforeAll
    static void extract() throws IOException {
        write("common-lib/src/main/java/demo/common/Messaging.java", """
                package demo.common;

                public final class Messaging {
                    public static final String PREFIX = "demo";
                    public static final String ORDER_EXCHANGE = PREFIX + ".order";
                    public static final String ORDER_CREATED = "order.created";
                    public static final String ORDER_PAID = "order.paid";
                    public static final String NOTIFY_QUEUE = "notify.order-events";
                    public static final String AUDIT_QUEUE = "audit.order-created";
                }
                """);
        write("common-lib/src/main/java/demo/common/Topology.java", """
                package demo.common;

                import org.springframework.amqp.core.*;

                public final class Topology {
                    public static TopicExchange topic(String name) {
                        return ExchangeBuilder.topicExchange(name).durable(true).build();
                    }

                    public static Queue queue(String name) {
                        return QueueBuilder.durable(name).deadLetterExchange("dlx").build();
                    }
                }
                """);

        write("order-svc/src/main/resources/application.yml", """
                spring:
                  application:
                    name: order-service
                """);
        write("order-svc/src/main/java/demo/order/OrderApp.java", """
                package demo.order;

                @SpringBootApplication
                public class OrderApp {
                }
                """);
        write("order-svc/src/main/java/demo/order/CatalogClient.java", """
                package demo.order;

                @FeignClient(name = "catalog-service", path = "/api/items")
                public interface CatalogClient {
                    @GetMapping("/{id}")
                    Item get(@PathVariable("id") long id);

                    @PostMapping("/search")
                    List<Item> search(@RequestBody Query query);

                    @GetMapping("/featured")
                    List<Item> featured();

                    @GetMapping("/missing/all")
                    void missing();
                }
                """);
        write("order-svc/src/main/java/demo/order/OrderService.java", """
                package demo.order;

                import demo.common.Messaging;

                @Service
                public class OrderService {
                    private final CatalogClient catalog;
                    private final OutboxService outboxService;
                    private final RabbitTemplate rabbitTemplate;

                    public void create(long itemId) {
                        catalog.get(itemId);
                        publish(Messaging.ORDER_CREATED, new OrderCreated(itemId));
                    }

                    public void pay() {
                        publish(Messaging.ORDER_PAID, new OrderPaid());
                    }

                    public void ship() {
                        rabbitTemplate.convertAndSend(Messaging.ORDER_EXCHANGE, "order." + "shipped", new OrderShipped());
                    }

                    public void remind() {
                        rabbitTemplate.invoke(operations -> {
                            operations.convertAndSend(Messaging.ORDER_EXCHANGE, "order.reminder", new OrderCreated(0));
                            return null;
                        });
                    }

                    public void forward(String routingKey) {
                        rabbitTemplate.convertAndSend(Messaging.ORDER_EXCHANGE, routingKey, new Object());
                    }

                    private void publish(String routingKey, Object event) {
                        outboxService.enqueue(Messaging.ORDER_EXCHANGE, routingKey, event, 1L);
                    }
                }
                """);

        write("order-svc/src/main/java/demo/order/OutboxRelay.java", """
                package demo.order;

                @Component
                public class OutboxRelay {
                    private final RabbitTemplate rabbitTemplate;

                    void relay(OutboxEvent event) {
                        rabbitTemplate.invoke(ops -> {
                            ops.convertAndSend(event.getExchange(), event.getRoutingKey(), event.getPayload());
                            return null;
                        });
                    }
                }
                """);

        write("catalog-svc/src/main/resources/application.yml", """
                spring:
                  application:
                    name: catalog-service
                """);
        write("catalog-svc/src/main/java/demo/catalog/CatalogApp.java", """
                package demo.catalog;

                @SpringBootApplication
                public class CatalogApp {
                }
                """);
        write("catalog-svc/src/main/java/demo/catalog/ItemController.java", """
                package demo.catalog;

                @RestController
                @RequestMapping("/api/items")
                public class ItemController {
                    @GetMapping("/{itemId}")
                    public Item get(@PathVariable long itemId) { return null; }

                    @GetMapping("/featured")
                    public List<Item> featured() { return null; }

                    @PostMapping("search")
                    public List<Item> search(@RequestBody Query query, Authentication auth) { return null; }

                    @RequestMapping(value = "/admin/{id}", method = RequestMethod.DELETE)
                    public void delete(@PathVariable long id, @RequestParam(name = "reason") String why) { }
                }
                """);
        write("catalog-svc/src/main/java/demo/catalog/InternalController.java", """
                package demo.catalog;

                @RestController
                @RequestMapping("/internal")
                public class InternalController {
                    @GetMapping("/stats")
                    public String stats() { return null; }
                }
                """);
        write("catalog-svc/src/main/java/demo/catalog/RabbitConfig.java", """
                package demo.catalog;

                import demo.common.Messaging;
                import demo.common.Topology;

                @Configuration
                public class RabbitConfig {
                    @Bean
                    TopicExchange orderExchange() {
                        return Topology.topic(Messaging.ORDER_EXCHANGE);
                    }

                    @Bean
                    Declarables notifyQueue() {
                        return consumer(Messaging.NOTIFY_QUEUE, orderExchange(), "order.*");
                    }

                    @Bean
                    Declarables auditQueue() {
                        return consumer(Messaging.AUDIT_QUEUE, orderExchange(), Messaging.ORDER_CREATED);
                    }

                    private Declarables consumer(String name, TopicExchange exchange, String... keys) {
                        Queue queue = Topology.queue(name);
                        List<Declarable> declarables = new ArrayList<>();
                        for (String key : keys) {
                            declarables.add(BindingBuilder.bind(queue).to(exchange).with(key));
                        }
                        return new Declarables(declarables);
                    }
                }
                """);
        write("catalog-svc/src/main/java/demo/catalog/NotifyListener.java", """
                package demo.catalog;

                import static demo.common.Messaging.NOTIFY_QUEUE;

                @Component
                @RabbitListener(queues = NOTIFY_QUEUE)
                public class NotifyListener {
                    @RabbitHandler
                    public void onCreated(OrderCreated event) { }

                    @RabbitHandler
                    public void onPaid(@Header("x-id") String id, @Payload OrderPaid paid) { }
                }
                """);
        write("catalog-svc/src/main/java/demo/catalog/AuditListener.java", """
                package demo.catalog;

                import demo.common.Messaging;

                @Component
                public class AuditListener {
                    @RabbitListener(queues = Messaging.AUDIT_QUEUE)
                    public void on(OrderCreated event, Message raw) { }
                }
                """);

        write("config/api-gateway.yml", """
                spring:
                  cloud:
                    gateway:
                      routes:
                        - id: catalog
                          uri: lb://catalog-service
                          predicates:
                            - Path=/api/items/**
                """);

        model = new ChannelExtractor(root, null).extract();
    }

    @Test
    void onlyModulesWithSpringBootApplicationAreServices() {
        assertThat(model.services()).containsExactly("catalog-service", "order-service");
        assertThat(model.gatewayConfig()).isEqualTo(Path.of("config/api-gateway.yml").toString());
        assertThat(model.warnings()).isEmpty();
    }

    @Test
    void endpointsCombineClassAndMethodMappings() {
        assertThat(model.endpoints())
                .extracting(Endpoint::httpMethod, Endpoint::path, Endpoint::exposedViaGateway)
                .containsExactlyInAnyOrder(
                        tuple("GET", "/api/items/{itemId}", true),
                        tuple("GET", "/api/items/featured", true),
                        tuple("POST", "/api/items/search", true),
                        tuple("DELETE", "/api/items/admin/{id}", true),
                        tuple("GET", "/internal/stats", false));

        assertThat(endpoint("DELETE /api/items/admin/{id}").params())
                .containsExactly(new Param("id", "long", "PATH"), new Param("reason", "String", "QUERY"));
        assertThat(endpoint("POST /api/items/search").params())
                .containsExactly(new Param("query", "Query", "BODY"), new Param("auth", "Authentication", "FRAMEWORK"));
        assertThat(endpoint("GET /api/items/{itemId}").handler().className()).isEqualTo("demo.catalog.ItemController");
        assertThat(endpoint("GET /api/items/{itemId}").handler().file())
                .isEqualTo("catalog-svc/src/main/java/demo/catalog/ItemController.java");
    }

    @Test
    void feignClientsAreLinkedToEndpointsOfTheTargetService() {
        HttpClientMethod get = client("get");
        assertThat(get.targetService()).isEqualTo("catalog-service");
        assertThat(get.path()).isEqualTo("/api/items/{id}");
        assertThat(get.callSites()).extracting(site -> site.method()).containsExactly("create");

        assertThat(channels("HTTP"))
                .extracting(Channel::from, Channel::to)
                .containsExactlyInAnyOrder(
                        tuple(get.id(), "ep:catalog-service:GET /api/items/{itemId}"),
                        // the literal endpoint wins over /api/items/{itemId}
                        tuple(client("featured").id(), "ep:catalog-service:GET /api/items/featured"),
                        tuple(client("search").id(), "ep:catalog-service:POST /api/items/search"));
        assertThat(unresolved("HTTP_CLIENT"))
                .extracting(Unresolved::detail)
                .containsExactly("no endpoint of catalog-service matches GET /api/items/missing/all");
    }

    @Test
    void publishersThroughPrivateHelpersGetTheValuesOfEachCaller() {
        assertThat(model.publishers())
                .extracting(Publisher::mechanism, Publisher::exchange, Publisher::routingKey, Publisher::payloadType,
                        p -> p.origin().method())
                .containsExactlyInAnyOrder(
                        tuple("OUTBOX", "demo.order", "order.created", "OrderCreated", "create"),
                        tuple("OUTBOX", "demo.order", "order.paid", "OrderPaid", "pay"),
                        tuple("RABBIT_TEMPLATE", "demo.order", "order.shipped", "OrderShipped", "ship"),
                        tuple("RABBIT_TEMPLATE", "demo.order", "order.reminder", "OrderCreated", "remind"));
        assertThat(model.publishers())
                .filteredOn(p -> p.mechanism().equals("OUTBOX"))
                .extracting(p -> p.sendSite().method())
                .containsOnly("publish");
        assertThat(unresolved("PUBLISHER"))
                .extracting(u -> u.site().method())
                .contains("forward");
        assertThat(unresolved("PUBLISHER_RELAY"))
                .extracting(u -> u.site().className())
                .containsExactly("demo.order.OutboxRelay");
    }

    @Test
    void bindingsKeepQueuesPairedWithTheirOwnRoutingKeys() {
        assertThat(model.bindings())
                .extracting(b -> b.queue(), b -> b.exchange(), b -> b.routingKey())
                .containsExactlyInAnyOrder(
                        tuple("notify.order-events", "demo.order", "order.*"),
                        tuple("audit.order-created", "demo.order", "order.created"));
    }

    @Test
    void listenersAreReadFromMethodAndClassLevelAnnotations() {
        assertThat(model.listeners())
                .extracting(Listener::queue, Listener::dispatch, Listener::payloadType, Listener::payloadParam)
                .containsExactlyInAnyOrder(
                        tuple("notify.order-events", "TYPE", "OrderCreated", "event"),
                        tuple("notify.order-events", "TYPE", "OrderPaid", "paid"),
                        tuple("audit.order-created", "METHOD", "OrderCreated", "event"));
    }

    @Test
    void messagesAreRoutedThroughBindingsAndHandlerTypes() {
        assertThat(channels("AMQP"))
                .extracting(c -> c.attributes().get("routingKey"), c -> c.attributes().get("queue"),
                        c -> handlerOf(c.to()), c -> c.attributes().get("payloadTypeMatch"))
                .containsExactlyInAnyOrder(
                        tuple("order.created", "notify.order-events", "onCreated", "EXACT"),
                        tuple("order.created", "audit.order-created", "on", "EXACT"),
                        tuple("order.paid", "notify.order-events", "onPaid", "EXACT"),
                        tuple("order.reminder", "notify.order-events", "onCreated", "EXACT"));
        // order.shipped reaches the notify queue, but no handler there accepts OrderShipped
        assertThat(unresolved("PUBLISHER"))
                .extracting(Unresolved::detail)
                .contains("no listener receives OrderShipped sent to demo.order with routing key order.shipped");
    }

    private static String handlerOf(String listenerId) {
        return model.listeners().stream()
                .filter(l -> l.id().equals(listenerId))
                .findFirst().orElseThrow()
                .handler().method();
    }

    private static Endpoint endpoint(String methodAndPath) {
        return model.endpoints().stream()
                .filter(e -> (e.httpMethod() + " " + e.path()).equals(methodAndPath))
                .findFirst().orElseThrow();
    }

    private static HttpClientMethod client(String method) {
        return model.httpClients().stream()
                .filter(c -> c.declaration().method().equals(method))
                .findFirst().orElseThrow();
    }

    private static List<Channel> channels(String kind) {
        return model.channels().stream().filter(c -> c.kind().equals(kind)).toList();
    }

    private static List<Unresolved> unresolved(String kind) {
        return model.unresolved().stream().filter(u -> u.kind().equals(kind)).toList();
    }

    private static void write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
