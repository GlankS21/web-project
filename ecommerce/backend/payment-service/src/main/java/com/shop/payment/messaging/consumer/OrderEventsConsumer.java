package com.shop.payment.messaging.consumer;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.payment.service.PaymentService;
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

    private final PaymentService paymentService;

    @RabbitListener(queues = MessagingConstants.PAYMENT_PAYMENT_REQUESTED_QUEUE)
    public void onPaymentRequested(PaymentRequestedEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY, event.eventId(), event.orderNumber());
        paymentService.processPaymentRequest(event);
    }

    @RabbitListener(queues = MessagingConstants.PAYMENT_ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.ORDER_CANCELLED_KEY, event.eventId(), event.orderNumber());
        paymentService.handleOrderCancelled(event);
    }
}
