package com.shop.inventory.service;

import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.inventory.dto.response.ReservationResponse;

import java.util.UUID;

/**
 * inventory-service side of the order saga. Every handler is idempotent: a redelivered event finds
 * the reservation of its order and does nothing more.
 */
public interface StockReservationService {

    /**
     * Reserves every item of the order, or nothing at all, then replies with {@code stock.reserved}
     * or {@code stock.reservation-failed} through the outbox.
     */
    void reserve(OrderCreatedEvent event);

    /**
     * The order was paid: reserved units leave the stock for good.
     */
    void commit(OrderConfirmedEvent event);

    /**
     * The order was cancelled: reserved units become available again and {@code stock.released}
     * is published. A cancellation that arrives before the reservation leaves a RELEASED marker, so
     * that a late {@code order.created} reserves nothing.
     */
    void release(OrderCancelledEvent event);

    ReservationResponse getReservation(UUID orderId);
}
