package com.shop.payment.dto.response;

import com.shop.payment.entity.PaymentMethod;
import com.shop.payment.entity.PaymentProvider;
import com.shop.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param confirmationToken initialises the YooKassa widget (or the mock card form) for the current
 *                          attempt, while the payment is {@code PENDING}
 * @param failureReason   code of the last decline, e.g. {@code insufficient_funds}
 * @param failureMessage  text of {@code failureReason} for the customer, in Russian
 * @param transactions    charge attempts and refunds, oldest first (empty in lists)
 */
public record PaymentResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        UUID userId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentProvider provider,
        PaymentStatus status,
        String confirmationToken,
        int attempts,
        String transactionId,
        String failureReason,
        String failureMessage,
        String refundTransactionId,
        Instant paidAt,
        Instant refundedAt,
        Instant createdAt,
        Instant updatedAt,
        List<PaymentTransactionResponse> transactions
) {
}
