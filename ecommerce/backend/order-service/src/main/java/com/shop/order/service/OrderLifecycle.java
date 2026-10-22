package com.shop.order.service;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.DomainEvent;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderDeliveredEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.common.event.PaymentSucceededEvent;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderStatus;
import com.shop.order.messaging.outbox.OutboxService;
import com.shop.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Saga steps of order-service: each one changes the status of the order and writes the matching
 * event to the outbox in the caller's transaction, so the state change and its event are committed
 * together. Callers must hold the row lock of the order.
 * <p>
 * The coupon of an order is released by the cancellation step, whatever triggered it.
 * <p>
 * The timeline notes and cancellation reasons are shown to the customer: they are written in Russian
 * from {@code messages_ru.properties} ({@link #message}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLifecycle {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final CouponLedger couponLedger;
    private final Clock clock;
    private final MessageSource messageSource;

    /**
     * Saga step 1: saves the new order as PENDING and asks inventory-service to reserve its stock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Order place(Order order) {
        Instant now = clock.instant();
        order.markPlaced(now);
        order = orderRepository.save(order);
        enqueue(MessagingConstants.ORDER_CREATED_KEY, order, new OrderCreatedEvent(UUID.randomUUID(), now,
                order.getId(), order.getOrderNumber(), order.getUserId(), order.getUserEmail(), itemsOf(order),
                order.getTotalAmount(), order.getCurrency()));
        return order;
    }

    /**
     * Saga step 3: stock is reserved, payment-service charges the order.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requestPayment(Order order) {
        Instant now = clock.instant();
        order.changeStatus(OrderStatus.AWAITING_PAYMENT, message("order.history.stock-reserved"), null, now);
        enqueue(MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY, order, new PaymentRequestedEvent(UUID.randomUUID(),
                now, order.getId(), order.getOrderNumber(), order.getUserId(), order.getUserEmail(),
                order.getTotalAmount(), order.getCurrency(), order.getPaymentMethod().name()));
    }

    /**
     * Saga success: inventory-service commits the reservation, notification-service informs the customer.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void paid(Order order, PaymentSucceededEvent payment) {
        Instant now = clock.instant();
        order.setPaymentId(payment.paymentId());
        order.setPaymentTransactionId(payment.transactionId());
        order.changeStatus(OrderStatus.PAID, message("order.history.payment-captured", payment.transactionId()),
                null, now);
        enqueue(MessagingConstants.ORDER_CONFIRMED_KEY, order, new OrderConfirmedEvent(UUID.randomUUID(), now,
                order.getId(), order.getOrderNumber(), order.getUserId(), order.getUserEmail(),
                order.getTotalAmount(), order.getCurrency()));
    }

    /**
     * End of the delivery flow, driven by an administrator: product-service records the purchased
     * products (only buyers may review them).
     *
     * @param note    optional remark recorded in the timeline
     * @param adminId administrator who confirmed the delivery
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void delivered(Order order, String note, UUID adminId) {
        Instant now = clock.instant();
        order.changeStatus(OrderStatus.DELIVERED, note, adminId, now);
        enqueue(MessagingConstants.ORDER_DELIVERED_KEY, order, new OrderDeliveredEvent(UUID.randomUUID(), now,
                order.getId(), order.getOrderNumber(), order.getUserId(), itemsOf(order)));
    }

    /**
     * Compensation: inventory-service releases any reservation (or returns the stock of a paid order),
     * payment-service refunds any captured payment (or refuses to charge later), notification-service
     * informs the customer. The coupon of the order, if any, can be used again.
     *
     * @param actorId user who cancelled, {@code null} for the saga and the expiry job
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(Order order, String reason, UUID actorId) {
        Instant now = clock.instant();
        order.changeStatus(OrderStatus.CANCELLED, reason, actorId, now);
        couponLedger.release(order);
        enqueue(MessagingConstants.ORDER_CANCELLED_KEY, order, new OrderCancelledEvent(UUID.randomUUID(), now,
                order.getId(), order.getOrderNumber(), order.getUserId(), order.getUserEmail(),
                order.getCancellationReason()));
        log.info("Order {} cancelled: {}", order.getOrderNumber(), order.getCancellationReason());
    }

    /**
     * @return the Russian text of {@code key} with its arguments
     */
    public String message(String key, Object... args) {
        return messageSource.getMessage(key, args, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }

    private static List<OrderItemPayload> itemsOf(Order order) {
        return order.getItems().stream()
                .map(item -> new OrderItemPayload(item.getProductId(), item.getSku(), item.getProductName(),
                        item.getQuantity(), item.getUnitPrice()))
                .toList();
    }

    private void enqueue(String routingKey, Order order, DomainEvent event) {
        outboxService.enqueue(MessagingConstants.ORDER_EXCHANGE, routingKey, event, order.getId());
    }
}
