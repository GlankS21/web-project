package ru.itmo.vkr.channels.model;

/**
 * A place that sends a message to an exchange. {@code mechanism} is {@code RABBIT_TEMPLATE} for a
 * direct send or {@code OUTBOX} for an event written to a transactional outbox and relayed later.
 * {@code origin} is where the routing key and payload were chosen (the outermost caller of a
 * private publishing helper); {@code sendSite} is the send call itself.
 */
public record Publisher(String id, String service, String mechanism, String exchange, String routingKey,
                        String payloadType, CodeRef origin, CodeRef sendSite) {
}
