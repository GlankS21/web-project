package com.shop.notification.entity;

/**
 * What triggered the notification.
 */
public enum NotificationType {

    /** {@code user.registered} */
    WELCOME,

    /** {@code order.created} */
    ORDER_PLACED,

    /** {@code order.confirmed}: the order is paid. */
    ORDER_CONFIRMED,

    /** {@code order.cancelled} */
    ORDER_CANCELLED
}
