package com.shop.common.constant;

/**
 * RabbitMQ topology contract shared by every service: exchanges, routing keys and queues.
 * <p>
 * Each producing domain owns one topic exchange. Consumers declare their own queues and bind
 * them to the producing exchange. Every queue is dead-lettered to {@link #DEAD_LETTER_EXCHANGE}
 * into a queue named {@code <queue>} + {@link #DEAD_LETTER_SUFFIX}.
 */
public final class MessagingConstants {

    // Exchanges
    public static final String ORDER_EXCHANGE = "shop.order.events";
    public static final String INVENTORY_EXCHANGE = "shop.inventory.events";
    public static final String PAYMENT_EXCHANGE = "shop.payment.events";
    public static final String USER_EXCHANGE = "shop.user.events";
    public static final String PRODUCT_EXCHANGE = "shop.product.events";
    public static final String DEAD_LETTER_EXCHANGE = "shop.dlx";

    // Routing keys published by order-service
    public static final String ORDER_CREATED_KEY = "order.created";
    public static final String ORDER_PAYMENT_REQUESTED_KEY = "order.payment-requested";
    public static final String ORDER_CONFIRMED_KEY = "order.confirmed";
    public static final String ORDER_CANCELLED_KEY = "order.cancelled";
    public static final String ORDER_DELIVERED_KEY = "order.delivered";

    // Routing keys published by inventory-service
    public static final String STOCK_RESERVED_KEY = "stock.reserved";
    public static final String STOCK_RESERVATION_FAILED_KEY = "stock.reservation-failed";
    public static final String STOCK_RELEASED_KEY = "stock.released";

    // Routing keys published by payment-service
    public static final String PAYMENT_SUCCEEDED_KEY = "payment.succeeded";
    public static final String PAYMENT_FAILED_KEY = "payment.failed";
    public static final String PAYMENT_REFUNDED_KEY = "payment.refunded";

    // Routing keys published by auth-service
    public static final String USER_REGISTERED_KEY = "user.registered";
    public static final String USER_PASSWORD_RESET_REQUESTED_KEY = "user.password-reset-requested";

    // Routing keys published by product-service
    public static final String PRODUCT_CREATED_KEY = "product.created";

    // Queues consumed by inventory-service
    public static final String INVENTORY_ORDER_CREATED_QUEUE = "inventory.order-created";
    public static final String INVENTORY_ORDER_CONFIRMED_QUEUE = "inventory.order-confirmed";
    public static final String INVENTORY_ORDER_CANCELLED_QUEUE = "inventory.order-cancelled";
    public static final String INVENTORY_PRODUCT_CREATED_QUEUE = "inventory.product-created";

    // Queues consumed by order-service
    public static final String ORDER_STOCK_RESERVED_QUEUE = "order.stock-reserved";
    public static final String ORDER_STOCK_RESERVATION_FAILED_QUEUE = "order.stock-reservation-failed";
    public static final String ORDER_PAYMENT_SUCCEEDED_QUEUE = "order.payment-succeeded";
    public static final String ORDER_PAYMENT_FAILED_QUEUE = "order.payment-failed";
    public static final String ORDER_PAYMENT_REFUNDED_QUEUE = "order.payment-refunded";

    // Queues consumed by payment-service
    public static final String PAYMENT_PAYMENT_REQUESTED_QUEUE = "payment.payment-requested";
    public static final String PAYMENT_ORDER_CANCELLED_QUEUE = "payment.order-cancelled";

    // Queues consumed by cart-service
    public static final String CART_ORDER_CREATED_QUEUE = "cart.order-created";

    // Queues consumed by product-service
    public static final String PRODUCT_ORDER_DELIVERED_QUEUE = "product.order-delivered";

    // Queues consumed by user-service
    public static final String USER_USER_REGISTERED_QUEUE = "user.user-registered";

    // Queues consumed by notification-service (one queue per producing exchange, several event types)
    public static final String NOTIFICATION_ORDER_EVENTS_QUEUE = "notification.order-events";
    public static final String NOTIFICATION_USER_EVENTS_QUEUE = "notification.user-events";

    public static final String DEAD_LETTER_SUFFIX = ".dlq";

    private MessagingConstants() {
    }
}
