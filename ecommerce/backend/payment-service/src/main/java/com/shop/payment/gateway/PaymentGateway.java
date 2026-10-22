package com.shop.payment.gateway;

import com.shop.payment.entity.PaymentProvider;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * External payment provider: YooKassa, or its local imitation selected with {@code PAYMENT_PROVIDER}.
 * <p>
 * The customer pays in the provider's widget embedded in the website, initialised with
 * {@link GatewayPayment#confirmationToken()}; the card never reaches the shop.
 * Every creating call carries an idempotence key, so that repeating it after a timeout returns the
 * operation created the first time instead of a second one. Implementations throw
 * {@link GatewayException} when the provider rejects a call or cannot be reached.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    GatewayPayment createPayment(CreatePaymentRequest request);

    /**
     * Current state of a payment, read from the provider (never from a notification body).
     */
    GatewayPayment getPayment(String paymentId);

    GatewayRefund createRefund(CreateRefundRequest request);

    GatewayRefund getRefund(String refundId);

    /**
     * @param metadata stored with the payment at the provider and returned with it
     */
    record CreatePaymentRequest(String idempotenceKey, UUID orderId, String orderNumber, BigDecimal amount,
                                String currency, String description, Map<String, String> metadata) {
    }

    /**
     * @param paymentId provider id of the captured payment to give back
     */
    record CreateRefundRequest(String idempotenceKey, String paymentId, BigDecimal amount, String currency,
                               String description) {
    }

    enum Status {
        PENDING,
        /** Authorised but not captured; not used, payments are created with automatic capture. */
        WAITING_FOR_CAPTURE,
        SUCCEEDED,
        CANCELED
    }

    /**
     * @param confirmationToken  initialises the payment widget, while the payment is pending
     * @param cancellationReason provider's code when cancelled, e.g. {@code insufficient_funds}
     */
    record GatewayPayment(String id, Status status, BigDecimal amount, String currency, String confirmationToken,
                          String cancellationReason, Map<String, String> metadata) {

        public GatewayPayment {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record GatewayRefund(String id, String paymentId, Status status, BigDecimal amount, String currency,
                         String cancellationReason) {
    }
}
