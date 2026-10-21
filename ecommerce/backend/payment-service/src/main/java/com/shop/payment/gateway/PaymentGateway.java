package com.shop.payment.gateway;

import com.shop.payment.entity.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * External payment provider. A real implementation would pass {@code orderId} as the provider's
 * idempotency key so that a retried charge is never captured twice.
 */
public interface PaymentGateway {

    ChargeResult charge(ChargeRequest request);

    /**
     * @return reference of the refund at the provider
     */
    String refund(String transactionId, BigDecimal amount, String currency);

    record ChargeRequest(UUID orderId, String orderNumber, BigDecimal amount, String currency, PaymentMethod method) {
    }

    /**
     * @param transactionId reference of the charge, present when approved
     * @param declineReason present when declined
     */
    record ChargeResult(boolean approved, String transactionId, String declineReason) {

        public static ChargeResult approved(String transactionId) {
            return new ChargeResult(true, transactionId, null);
        }

        public static ChargeResult declined(String reason) {
            return new ChargeResult(false, null, reason);
        }
    }
}
