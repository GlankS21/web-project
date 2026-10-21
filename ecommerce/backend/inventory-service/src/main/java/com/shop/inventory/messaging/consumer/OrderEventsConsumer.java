package com.shop.inventory.messaging.consumer;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.inventory.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Saga steps driven by order-service. Failures are retried by the listener container (see shared
 * config) and then dead-lettered to {@code <queue>.dlq}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private final StockReservationService stockReservationService;

    @RabbitListener(queues = MessagingConstants.INVENTORY_ORDER_CREATED_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.ORDER_CREATED_KEY, event.eventId(), event.orderNumber());
        stockReservationService.reserve(event);
    }

    @RabbitListener(queues = MessagingConstants.INVENTORY_ORDER_CONFIRMED_QUEUE)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.ORDER_CONFIRMED_KEY, event.eventId(), event.orderNumber());
        stockReservationService.commit(event);
    }

    @RabbitListener(queues = MessagingConstants.INVENTORY_ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.ORDER_CANCELLED_KEY, event.eventId(), event.orderNumber());
        stockReservationService.release(event);
    }
}
