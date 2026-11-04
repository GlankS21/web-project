package ru.itmo.vkr.channels.model;

import java.util.List;

/**
 * The inter-service channel model of a whole system.
 */
public record ChannelModel(String root, String gatewayConfig, List<String> services, List<Endpoint> endpoints,
                           List<HttpClientMethod> httpClients, List<Publisher> publishers,
                           List<QueueBinding> bindings, List<Listener> listeners, List<Channel> channels,
                           List<Unresolved> unresolved, List<String> warnings) {
}
