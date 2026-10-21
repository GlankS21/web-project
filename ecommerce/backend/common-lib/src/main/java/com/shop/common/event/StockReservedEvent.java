package com.shop.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga step 2 (success): inventory-service reserved stock for every item of the order.
 */
public record StockReservedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber
) implements DomainEvent {
}
