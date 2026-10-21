package com.shop.payment.service;

import com.shop.common.dto.PageResponse;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.common.security.AuthenticatedUser;
import com.shop.payment.dto.response.PaymentResponse;
import com.shop.payment.entity.PaymentStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * payment-service side of the order saga. Each order has at most one payment row, so every handler
 * is idempotent under redelivery.
 */
public interface PaymentService {

    /**
     * Charges the order once and replies with {@code payment.succeeded} or {@code payment.failed}
     * through the outbox. An order already cancelled is never charged.
     */
    void processPaymentRequest(PaymentRequestedEvent event);

    /**
     * Refunds a captured payment of a cancelled order, or records that the order must not be charged
     * if its payment request arrives later.
     */
    void handleOrderCancelled(OrderCancelledEvent event);

    /**
     * Customers only see the payment of their own orders (others are reported as not found).
     */
    PaymentResponse getPaymentOfOrder(UUID orderId, AuthenticatedUser caller);

    PageResponse<PaymentResponse> searchPayments(PaymentStatus status, Pageable pageable);
}
