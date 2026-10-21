package com.shop.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Saga step 1: order-service created an order in PENDING state.
 * Consumed by inventory-service to reserve stock.
 */
public record OrderCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String userEmail,
        List<OrderItemPayload> items,
        BigDecimal totalAmount,
        String currency
) implements DomainEvent {
}
