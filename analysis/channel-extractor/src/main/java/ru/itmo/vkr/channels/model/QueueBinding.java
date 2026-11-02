package ru.itmo.vkr.channels.model;

/**
 * A queue bound to an exchange. {@code routingKey} is the binding pattern ({@code *} and {@code #}
 * wildcards for topic exchanges).
 */
public record QueueBinding(String service, String queue, String exchange, String routingKey, CodeRef site) {
}
