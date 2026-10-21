package com.shop.order.entity;

import java.util.Set;

/**
 * Life cycle of an order.
 * <pre>
 * PENDING ──stock.reserved──► AWAITING_PAYMENT ──payment.succeeded──► PAID ──► SHIPPED ──► DELIVERED
 *    │                              │
 *    └── stock.reservation-failed,  └── payment.failed, customer cancellation, expiry ──► CANCELLED
 *        customer cancellation, expiry
 * </pre>
 */
public enum OrderStatus {

    /** Created; waiting for inventory-service to reserve the stock. */
    PENDING,

    /** Stock reserved; waiting for payment-service. */
    AWAITING_PAYMENT,

    /** Paid; the reserved stock has been committed. */
    PAID,

    SHIPPED,

    DELIVERED,

    /** Final: stock released, any captured payment refunded. */
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTargets().contains(target);
    }

    /**
     * An order can be cancelled until it is paid. After payment it follows the delivery flow.
     */
    public boolean isCancellable() {
        return canTransitionTo(CANCELLED);
    }

    /**
     * Still inside the saga: the outcome (PAID or CANCELLED) is not known yet.
     */
    public boolean isInProgress() {
        return this == PENDING || this == AWAITING_PAYMENT;
    }

    private Set<OrderStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> Set.of(AWAITING_PAYMENT, CANCELLED);
            case AWAITING_PAYMENT -> Set.of(PAID, CANCELLED);
            case PAID -> Set.of(SHIPPED);
            case SHIPPED -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }
}
