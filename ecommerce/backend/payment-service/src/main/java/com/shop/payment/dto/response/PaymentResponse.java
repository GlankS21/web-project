package com.shop.payment.dto.response;

import com.shop.payment.entity.PaymentMethod;
import com.shop.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        UUID userId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        String transactionId,
        String failureReason,
        String refundTransactionId,
        Instant refundedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
