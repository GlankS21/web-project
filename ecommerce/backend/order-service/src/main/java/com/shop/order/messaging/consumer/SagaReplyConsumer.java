package com.shop.order.messaging.consumer;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.PaymentFailedEvent;
import com.shop.common.event.PaymentRefundedEvent;
import com.shop.common.event.PaymentSucceededEvent;
import com.shop.common.event.StockReservationFailedEvent;
import com.shop.common.event.StockReservedEvent;
import com.shop.order.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Replies of inventory-service and payment-service. Failures are retried by the listener container
 * (see shared config) and then dead-lettered to {@code <queue>.dlq}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaReplyConsumer {

    private final OrderSagaService orderSagaService;

    @RabbitListener(queues = MessagingConstants.ORDER_STOCK_RESERVED_QUEUE)
    public void onStockReserved(StockReservedEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.STOCK_RESERVED_KEY, event.eventId(), event.orderNumber());
        orderSagaService.onStockReserved(event);
    }

    @RabbitListener(queues = MessagingConstants.ORDER_STOCK_RESERVATION_FAILED_QUEUE)
    public void onStockReservationFailed(StockReservationFailedEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.STOCK_RESERVATION_FAILED_KEY, event.eventId(), event.orderNumber());
        orderSagaService.onStockReservationFailed(event);
    }

    @RabbitListener(queues = MessagingConstants.ORDER_PAYMENT_SUCCEEDED_QUEUE)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.PAYMENT_SUCCEEDED_KEY, event.eventId(), event.orderNumber());
        orderSagaService.onPaymentSucceeded(event);
    }

    @RabbitListener(queues = MessagingConstants.ORDER_PAYMENT_FAILED_QUEUE)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.PAYMENT_FAILED_KEY, event.eventId(), event.orderNumber());
        orderSagaService.onPaymentFailed(event);
    }

    @RabbitListener(queues = MessagingConstants.ORDER_PAYMENT_REFUNDED_QUEUE)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        log.info("Received {} (event {}) for order {}",
                MessagingConstants.PAYMENT_REFUNDED_KEY, event.eventId(), event.orderNumber());
        orderSagaService.onPaymentRefunded(event);
    }
}
