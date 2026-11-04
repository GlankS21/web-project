package ru.itmo.vkr.channels;

import ru.itmo.vkr.channels.model.Channel;
import ru.itmo.vkr.channels.model.Endpoint;
import ru.itmo.vkr.channels.model.HttpClientMethod;
import ru.itmo.vkr.channels.model.Listener;
import ru.itmo.vkr.channels.model.Publisher;
import ru.itmo.vkr.channels.model.QueueBinding;
import ru.itmo.vkr.channels.model.Unresolved;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Connects the two ends of every inter-service interaction: client methods to the endpoints they
 * call, and publishers to the listeners their messages are routed to.
 */
final class ChannelLinker {

    record Result(List<Channel> channels, List<Unresolved> unresolved) {
    }

    Result link(List<String> services, List<Endpoint> endpoints, List<HttpClientMethod> clients,
                List<Publisher> publishers, List<QueueBinding> bindings, List<Listener> listeners) {
        List<Channel> channels = new ArrayList<>();
        List<Unresolved> unresolved = new ArrayList<>();
        linkHttp(services, endpoints, clients, channels, unresolved);
        linkAmqp(publishers, bindings, listeners, channels, unresolved);
        return new Result(channels, unresolved);
    }

    private static void linkHttp(List<String> services, List<Endpoint> endpoints, List<HttpClientMethod> clients,
                                 List<Channel> channels, List<Unresolved> unresolved) {
        for (HttpClientMethod client : clients) {
            if (client.url() != null && !client.url().isBlank()) {
                unresolved.add(new Unresolved(client.service(), "HTTP_CLIENT",
                        "fixed url " + client.url() + ", treated as an external system", client.declaration()));
                continue;
            }
            if (!services.contains(client.targetService())) {
                unresolved.add(new Unresolved(client.service(), "HTTP_CLIENT",
                        "target service '" + client.targetService() + "' is not part of the analysed system",
                        client.declaration()));
                continue;
            }
            List<Endpoint> matches = endpoints.stream()
                    .filter(endpoint -> endpoint.service().equals(client.targetService()))
                    .filter(endpoint -> methodsMatch(endpoint.httpMethod(), client.httpMethod()))
                    .filter(endpoint -> Patterns.pathTemplatesMatch(endpoint.path(), client.path()))
                    .toList();
            int closest = matches.stream()
                    .mapToInt(endpoint -> Patterns.segmentKindMismatches(endpoint.path(), client.path()))
                    .min().orElse(0);
            for (Endpoint endpoint : matches) {
                if (Patterns.segmentKindMismatches(endpoint.path(), client.path()) == closest) {
                    Map<String, String> attributes = new LinkedHashMap<>();
                    attributes.put("httpMethod", client.httpMethod());
                    attributes.put("clientPath", client.path());
                    attributes.put("endpointPath", endpoint.path());
                    channels.add(new Channel("HTTP", client.id(), endpoint.id(), attributes));
                }
            }
            if (matches.isEmpty()) {
                unresolved.add(new Unresolved(client.service(), "HTTP_CLIENT",
                        "no endpoint of " + client.targetService() + " matches " + client.httpMethod() + " "
                                + client.path(), client.declaration()));
            }
        }
    }

    private static boolean methodsMatch(String endpointMethod, String clientMethod) {
        return endpointMethod.equals("ANY") || clientMethod.equals("ANY") || endpointMethod.equals(clientMethod);
    }

    private static void linkAmqp(List<Publisher> publishers, List<QueueBinding> bindings, List<Listener> listeners,
                                 List<Channel> channels, List<Unresolved> unresolved) {
        for (Publisher publisher : publishers) {
            // queue -> binding key that routed the message there
            Map<String, String> queues = new LinkedHashMap<>();
            if (publisher.exchange().equals(AmqpExtractor.DEFAULT_EXCHANGE)) {
                queues.put(publisher.routingKey(), "(default exchange)");
            } else {
                for (QueueBinding binding : bindings) {
                    if (binding.exchange().equals(publisher.exchange())
                            && Patterns.topicMatches(binding.routingKey(), publisher.routingKey())) {
                        queues.putIfAbsent(binding.queue(), binding.routingKey());
                    }
                }
            }
            boolean linked = false;
            for (Map.Entry<String, String> queue : queues.entrySet()) {
                for (Listener listener : listeners) {
                    if (!listener.queue().equals(queue.getKey())) {
                        continue;
                    }
                    String typeMatch = typeMatch(publisher.payloadType(), listener.payloadType());
                    // a @RabbitHandler is only invoked for its own payload type
                    if (listener.dispatch().equals("TYPE") && typeMatch.equals("DIFFERENT")) {
                        continue;
                    }
                    Map<String, String> attributes = new LinkedHashMap<>();
                    attributes.put("exchange", publisher.exchange());
                    attributes.put("routingKey", publisher.routingKey());
                    attributes.put("bindingKey", queue.getValue());
                    attributes.put("queue", queue.getKey());
                    attributes.put("payloadType", publisher.payloadType());
                    attributes.put("payloadTypeMatch", typeMatch);
                    channels.add(new Channel("AMQP", publisher.id(), listener.id(), attributes));
                    linked = true;
                }
            }
            if (!linked) {
                unresolved.add(new Unresolved(publisher.service(), "PUBLISHER",
                        "no listener receives " + publisher.payloadType() + " sent to " + publisher.exchange()
                                + " with routing key " + publisher.routingKey(), publisher.origin()));
            }
        }
    }

    private static String typeMatch(String sent, String received) {
        if (sent == null || received == null) {
            return "UNKNOWN";
        }
        return Objects.equals(sent, received) ? "EXACT" : "DIFFERENT";
    }
}
