package com.shop.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Saga step 3: order-service asks payment-service to charge the order once stock is reserved.
 */
public record PaymentRequestedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String userEmail,
        BigDecimal amount,
        String currency,
        String paymentMethod
) implements DomainEvent {
}
