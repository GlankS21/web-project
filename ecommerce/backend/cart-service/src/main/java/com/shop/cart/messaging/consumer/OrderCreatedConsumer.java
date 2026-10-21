package com.shop.cart.messaging.consumer;

import com.shop.cart.service.CartService;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Removes ordered products from the cart of the customer. Failures are retried by the listener
 * container (see shared config) and then dead-lettered to {@code cart.order-created.dlq}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private final CartService cartService;

    @RabbitListener(queues = MessagingConstants.CART_ORDER_CREATED_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.ORDER_CREATED_KEY, event.eventId(), event.orderNumber());
        cartService.removeOrderedProducts(event);
    }
}
