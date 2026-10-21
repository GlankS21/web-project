package com.shop.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Saga step 4 (success): the payment was captured.
 */
public record PaymentSucceededEvent(
        UUID eventId,
        Instant occurredAt,
        UUID paymentId,
        UUID orderId,
        String orderNumber,
        String transactionId,
        BigDecimal amount,
        String currency
) implements DomainEvent {
}
