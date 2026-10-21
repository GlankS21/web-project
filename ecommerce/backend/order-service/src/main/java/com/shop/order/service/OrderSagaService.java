package com.shop.order.service;

import com.shop.common.event.PaymentFailedEvent;
import com.shop.common.event.PaymentSucceededEvent;
import com.shop.common.event.StockReservationFailedEvent;
import com.shop.common.event.StockReservedEvent;

import java.util.List;
import java.util.UUID;

/**
 * order-service side of the saga. Every handler locks the order and acts only when the order is in
 * the status the event answers, so redelivered or late events change nothing.
 */
public interface OrderSagaService {

    /** PENDING → AWAITING_PAYMENT, publishes {@code order.payment-requested}. */
    void onStockReserved(StockReservedEvent event);

    /** PENDING → CANCELLED, publishes {@code order.cancelled}. */
    void onStockReservationFailed(StockReservationFailedEvent event);

    /** AWAITING_PAYMENT → PAID, publishes {@code order.confirmed}. */
    void onPaymentSucceeded(PaymentSucceededEvent event);

    /** AWAITING_PAYMENT → CANCELLED, publishes {@code order.cancelled} (stock is released). */
    void onPaymentFailed(PaymentFailedEvent event);

    /**
     * Ids of orders still waiting for stock or payment after the configured timeout, oldest first.
     */
    List<UUID> findExpiredOrderIds(int limit);

    /**
     * Cancels the order if it is still waiting; does nothing when it moved on in the meantime.
     *
     * @return whether the order was cancelled
     */
    boolean expireOrder(UUID orderId);
}
