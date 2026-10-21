package com.shop.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Compensation: inventory-service released a previous reservation after the order was cancelled.
 */
public record StockReleasedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber
) implements DomainEvent {
}
