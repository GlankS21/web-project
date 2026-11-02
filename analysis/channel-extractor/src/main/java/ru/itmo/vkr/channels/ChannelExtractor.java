package ru.itmo.vkr.channels;

import ru.itmo.vkr.channels.model.ChannelModel;
import ru.itmo.vkr.channels.model.Endpoint;
import ru.itmo.vkr.channels.model.HttpClientMethod;
import ru.itmo.vkr.channels.model.Listener;
import ru.itmo.vkr.channels.model.Publisher;
import ru.itmo.vkr.channels.model.QueueBinding;
import ru.itmo.vkr.channels.model.Unresolved;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds the {@link ChannelModel} of the Spring microservice system under a root directory.
 */
public final class ChannelExtractor {

    private static final Set<String> GATEWAY_CONFIG_NAMES = Set.of("api-gateway.yml", "api-gateway.yaml");

    private final Path root;
    private final Path gatewayConfig;

    /**
     * @param root          directory holding the service modules
     * @param gatewayConfig Spring Cloud Gateway configuration; {@code null} to look for an
     *                      {@code api-gateway.yml} under the root
     */
    public ChannelExtractor(Path root, Path gatewayConfig) {
        this.root = root.toAbsolutePath().normalize();
        this.gatewayConfig = gatewayConfig == null ? null : gatewayConfig.toAbsolutePath().normalize();
    }

    public ChannelModel extract() throws IOException {
        SourceIndex index = SourceIndex.load(root);
        ValueResolver resolver = new ValueResolver(index);
        List<String> warnings = new ArrayList<>(index.warnings());

        Path gatewayFile = gatewayConfig != null ? gatewayConfig : findGatewayConfig().orElse(null);
        GatewayRoutes gateway = gatewayFile == null ? GatewayRoutes.none() : GatewayRoutes.load(gatewayFile);
        if (gatewayFile == null) {
            warnings.add("No gateway configuration found: endpoint exposure is unknown");
        }

        SpringWebExtractor web = new SpringWebExtractor(root, resolver, gateway);
        AmqpExtractor amqp = new AmqpExtractor(root, resolver, AmqpExtractor.DEFAULT_OUTBOX_SIGNATURES);
        List<String> services = new ArrayList<>();
        List<Endpoint> endpoints = new ArrayList<>();
        List<HttpClientMethod> clients = new ArrayList<>();
        List<Publisher> publishers = new ArrayList<>();
        List<QueueBinding> bindings = new ArrayList<>();
        List<Listener> listeners = new ArrayList<>();
        List<Unresolved> unresolved = new ArrayList<>();
        for (SourceIndex.Module service : index.services()) {
            services.add(service.name());
            SpringWebExtractor.Result webResult = web.extract(service);
            endpoints.addAll(webResult.endpoints());
            clients.addAll(webResult.clients());
            unresolved.addAll(webResult.unresolved());
            AmqpExtractor.Result amqpResult = amqp.extract(service);
            publishers.addAll(amqpResult.publishers());
            bindings.addAll(amqpResult.bindings());
            listeners.addAll(amqpResult.listeners());
            unresolved.addAll(amqpResult.unresolved());
        }

        ChannelLinker.Result links = new ChannelLinker()
                .link(services, endpoints, clients, publishers, bindings, listeners);
        unresolved.addAll(links.unresolved());
        return new ChannelModel(root.toString(), gatewayFile == null ? null : root.relativize(gatewayFile).toString(),
                services, endpoints, clients, publishers, bindings, listeners, links.channels(), unresolved,
                warnings);
    }

    private Optional<Path> findGatewayConfig() throws IOException {
        try (Stream<Path> paths = Files.walk(root, 8)) {
            return paths.filter(Files::isRegularFile)
                    .filter(file -> GATEWAY_CONFIG_NAMES.contains(file.getFileName().toString()))
                    .filter(file -> !root.relativize(file).toString().replace('\\', '/').contains("/target/"))
                    .sorted()
                    .findFirst();
        }
    }
}
