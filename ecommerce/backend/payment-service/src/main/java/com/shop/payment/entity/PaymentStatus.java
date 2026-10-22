package com.shop.payment.entity;

/**
 * Status of the payment of an order.
 * <pre>
 * PENDING ──captured──► SUCCEEDED ──order cancelled──► REFUND_PENDING ──► REFUNDED
 *  │   ▲                                                      └──────► REFUND_FAILED
 *  ▼   │ new attempt
 * FAILED (attempt declined or abandoned: the customer may pay again until the order expires)
 * </pre>
 * {@code CANCELLED} records an order cancelled before any capture (or a cash-on-delivery order
 * cancelled before delivery), so that a late attempt is refunded instead of kept.
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED,
    CANCELLED
}
