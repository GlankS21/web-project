package com.shop.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga step 2 (failure): inventory-service could not reserve stock; nothing was reserved.
 */
public record StockReservationFailedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        String reason
) implements DomainEvent {
}
