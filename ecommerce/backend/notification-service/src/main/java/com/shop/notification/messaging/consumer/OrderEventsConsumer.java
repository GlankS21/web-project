package com.shop.notification.messaging.consumer;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.notification.service.NotificationService;
import com.shop.notification.template.NotificationTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * One queue carries the three order events; the handler is chosen from the event type in the message
 * headers. Failures are retried by the listener container and then dead-lettered to
 * {@code notification.order-events.dlq}. A failed e-mail delivery is not a failure here: it is
 * recorded and retried by the notification retry job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = MessagingConstants.NOTIFICATION_ORDER_EVENTS_QUEUE)
public class OrderEventsConsumer {

    private final NotificationService notificationService;
    private final NotificationTemplates templates;

    @RabbitHandler
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.ORDER_CREATED_KEY, event.eventId(), event.orderNumber());
        notificationService.notify(event.eventId(), templates.orderPlaced(event));
    }

    @RabbitHandler
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.ORDER_CONFIRMED_KEY, event.eventId(), event.orderNumber());
        notificationService.notify(event.eventId(), templates.orderConfirmed(event));
    }

    @RabbitHandler
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.ORDER_CANCELLED_KEY, event.eventId(), event.orderNumber());
        notificationService.notify(event.eventId(), templates.orderCancelled(event));
    }
}
