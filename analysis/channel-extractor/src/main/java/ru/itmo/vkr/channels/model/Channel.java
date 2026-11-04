package ru.itmo.vkr.channels.model;

import java.util.Map;

/**
 * A resolved edge between services: {@code HTTP} from a client method to an endpoint, or
 * {@code AMQP} from a publisher to a listener.
 */
public record Channel(String kind, String from, String to, Map<String, String> attributes) {
}
