package com.shop.payment.service;

import com.shop.common.dto.PageResponse;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.common.security.AuthenticatedUser;
import com.shop.payment.dto.response.MockCardPaymentResponse;
import com.shop.payment.dto.response.PaymentResponse;
import com.shop.payment.entity.PaymentStatus;
import org.springframework.data.domain.Pageable;

import java.time.YearMonth;
import java.util.UUID;

/**
 * payment-service side of the order saga, and the customer's actions on the payment page. Each order
 * has at most one payment, so every handler is idempotent under redelivery; every operation at the
 * provider carries the id of its transaction as idempotence key.
 */
public interface PaymentService {

    /**
     * Cash on delivery is accepted at once ({@code payment.succeeded}). An online payment is created at
     * the provider; the customer pays in the provider's widget and the outcome arrives later by webhook,
     * by the website asking for a refresh, or by reconciliation. An order already cancelled is never
     * charged.
     */
    void processPaymentRequest(PaymentRequestedEvent event);

    /**
     * Refunds a captured payment of a cancelled order (asynchronously, {@code payment.refunded} when
     * done), or records that a later capture must be refunded.
     */
    void handleOrderCancelled(OrderCancelledEvent event);

    /**
     * Customers only see the payment of their own orders (others are reported as not found).
     */
    PaymentResponse getPaymentOfOrder(UUID orderId, AuthenticatedUser caller);

    /**
     * New attempt, e.g. with another card, after a declined or abandoned online payment. While an
     * attempt is pending, returns it unchanged (its widget token can still be used).
     */
    PaymentResponse retryPayment(UUID orderId, AuthenticatedUser caller);

    /**
     * Reads the state of the pending operations from the provider, e.g. when the widget reports the end
     * of the payment before the webhook arrived.
     */
    PaymentResponse refreshPayment(UUID orderId, AuthenticatedUser caller);

    /**
     * Webhook of the provider. The notification is stored with its original body; the notified object
     * is read again from the provider's API, the body is never trusted. A final notification that was
     * already processed is recorded as a duplicate and not handled again.
     *
     * @param event    e.g. {@code payment.succeeded}, {@code payment.canceled}, {@code refund.succeeded}
     * @param objectId id of the payment or refund at the provider
     * @param payload  body of the notification as received
     */
    void handleProviderNotification(String event, String objectId, String payload);

    /**
     * Card typed in the mock payment form ({@code PAYMENT_PROVIDER=mock} only); the outcome follows the
     * table of YooKassa test cards. Only the owner of the order.
     *
     * @param providerPaymentId id of the mock payment
     * @param threeDSecureCode  code of the imitated 3-D Secure page, once the card asked for it
     */
    MockCardPaymentResponse payWithMockCard(String providerPaymentId, String cardNumber, YearMonth expiry,
                                            String threeDSecureCode, AuthenticatedUser caller);

    /**
     * Checks operations the provider has not reported on yet, re-sends those that never reached it and
     * gives up those pending for too long.
     *
     * @return number of operations checked
     */
    int reconcilePendingTransactions();

    PageResponse<PaymentResponse> searchPayments(PaymentStatus status, Pageable pageable);
}
