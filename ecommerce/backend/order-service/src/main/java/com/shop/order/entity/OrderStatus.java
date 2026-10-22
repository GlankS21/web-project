package com.shop.order.entity;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;

import java.util.Set;

/**
 * Life cycle of an order.
 * <pre>
 * PENDING ──stock.reserved──► AWAITING_PAYMENT ──payment.succeeded──► PAID ──► SHIPPED ──► DELIVERED
 *    │                              │                                      │
 *    └── stock.reservation-failed,  └── payment.failed, cancellation,      └── cancellation (stock returned,
 *        cancellation, expiry           expiry ──► CANCELLED                   payment refunded) ──► CANCELLED
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

    /** Final: stock released or returned, any captured payment refunded. */
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTargets().contains(target);
    }

    /**
     * An order can be cancelled until it is shipped; a paid order is refunded.
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

    /**
     * Russian name of the status ({@code order.status.<STATUS>} of {@code messages_ru.properties}), to be
     * passed as a message argument.
     */
    public MessageSourceResolvable label() {
        return new DefaultMessageSourceResolvable(new String[]{"order.status." + name()}, name());
    }

    private Set<OrderStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> Set.of(AWAITING_PAYMENT, CANCELLED);
            case AWAITING_PAYMENT -> Set.of(PAID, CANCELLED);
            case PAID -> Set.of(SHIPPED, CANCELLED);
            case SHIPPED -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }
}
