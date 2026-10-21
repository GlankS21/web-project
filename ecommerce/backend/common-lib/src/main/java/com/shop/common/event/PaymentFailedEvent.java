package com.shop.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Saga step 4 (failure): the payment was declined; order-service must compensate.
 */
public record PaymentFailedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID paymentId,
        UUID orderId,
        String orderNumber,
        BigDecimal amount,
        String currency,
        String reason
) implements DomainEvent {
}
