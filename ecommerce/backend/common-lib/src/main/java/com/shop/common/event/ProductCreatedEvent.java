package com.shop.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * product-service created a product. inventory-service opens an empty stock record for it.
 */
public record ProductCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        Long productId,
        String sku,
        String name
) implements DomainEvent {
}
