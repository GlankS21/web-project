package com.shop.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Common contract of every event published on RabbitMQ.
 * {@link #eventId()} lets consumers detect and skip duplicate deliveries.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();
}
