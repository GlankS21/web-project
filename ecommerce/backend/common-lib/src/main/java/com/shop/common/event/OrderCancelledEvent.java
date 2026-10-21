package com.shop.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The order was CANCELLED (by the saga or by the user). inventory-service releases any reservation.
 */
public record OrderCancelledEvent(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String userEmail,
        String reason
) implements DomainEvent {
}
