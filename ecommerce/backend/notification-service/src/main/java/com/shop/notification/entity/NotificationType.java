package com.shop.notification.entity;

/**
 * What triggered the notification.
 */
public enum NotificationType {

    /** {@code user.registered} */
    WELCOME,

    /** {@code user.password-reset-requested}: the body holds a single-use reset link. */
    PASSWORD_RESET(true),

    /** {@code order.created} */
    ORDER_PLACED,

    /** {@code order.confirmed}: the order is paid. */
    ORDER_CONFIRMED,

    /** {@code order.cancelled} */
    ORDER_CANCELLED;

    private final boolean confidentialBody;

    NotificationType() {
        this(false);
    }

    NotificationType(boolean confidentialBody) {
        this.confidentialBody = confidentialBody;
    }

    /**
     * Whether the body holds a secret (such as a password reset link) and must not be returned by the
     * API. It is still stored, so that the e-mail can be delivered again.
     */
    public boolean hasConfidentialBody() {
        return confidentialBody;
    }
}
