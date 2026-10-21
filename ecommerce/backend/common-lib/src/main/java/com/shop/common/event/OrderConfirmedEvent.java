package com.shop.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Saga completed successfully: the order is PAID. inventory-service commits the reservation.
 */
public record OrderConfirmedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String userEmail,
        BigDecimal totalAmount,
        String currency
) implements DomainEvent {
}
