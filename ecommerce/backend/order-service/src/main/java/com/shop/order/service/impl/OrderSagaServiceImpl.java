package com.shop.order.service.impl;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.PaymentFailedEvent;
import com.shop.common.event.PaymentSucceededEvent;
import com.shop.common.event.StockReservationFailedEvent;
import com.shop.common.event.StockReservedEvent;
import com.shop.order.config.OrderProperties;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderStatus;
import com.shop.order.repository.OrderRepository;
import com.shop.order.service.OrderLifecycle;
import com.shop.order.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaServiceImpl implements OrderSagaService {

    private final OrderRepository orderRepository;
    private final OrderLifecycle orderLifecycle;
    private final OrderProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public void onStockReserved(StockReservedEvent event) {
        lockOrder(event.orderId(), MessagingConstants.STOCK_RESERVED_KEY).ifPresent(order -> {
            if (expect(order, OrderStatus.PENDING, MessagingConstants.STOCK_RESERVED_KEY)) {
                orderLifecycle.requestPayment(order);
                log.info("Stock reserved for order {}, payment requested", order.getOrderNumber());
            }
        });
    }

    @Override
    @Transactional
    public void onStockReservationFailed(StockReservationFailedEvent event) {
        lockOrder(event.orderId(), MessagingConstants.STOCK_RESERVATION_FAILED_KEY).ifPresent(order -> {
            if (expect(order, OrderStatus.PENDING, MessagingConstants.STOCK_RESERVATION_FAILED_KEY)) {
                orderLifecycle.cancel(order, event.reason(), null);
            }
        });
    }

    @Override
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        lockOrder(event.orderId(), MessagingConstants.PAYMENT_SUCCEEDED_KEY).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                // payment-service refunds it: it consumes order.cancelled as well
                log.warn("Payment {} captured for order {} that is already cancelled; it will be refunded",
                        event.paymentId(), order.getOrderNumber());
                return;
            }
            if (expect(order, OrderStatus.AWAITING_PAYMENT, MessagingConstants.PAYMENT_SUCCEEDED_KEY)) {
                orderLifecycle.paid(order, event);
                log.info("Order {} paid (transaction {})", order.getOrderNumber(), event.transactionId());
            }
        });
    }

    @Override
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        lockOrder(event.orderId(), MessagingConstants.PAYMENT_FAILED_KEY).ifPresent(order -> {
            if (expect(order, OrderStatus.AWAITING_PAYMENT, MessagingConstants.PAYMENT_FAILED_KEY)) {
                orderLifecycle.cancel(order, "Payment failed: " + event.reason(), null);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findExpiredOrderIds(int limit) {
        return orderRepository.findIdsByStatusInAndCreatedAtBefore(
                EnumSet.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT),
                clock.instant().minus(properties.paymentTimeout()),
                PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public boolean expireOrder(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .filter(order -> order.getStatus().isInProgress())
                .map(order -> {
                    orderLifecycle.cancel(order, "Not confirmed within %d minutes"
                            .formatted(properties.paymentTimeout().toMinutes()), null);
                    return true;
                })
                .orElse(false);
    }

    private Optional<Order> lockOrder(UUID orderId, String routingKey) {
        Optional<Order> order = orderRepository.findByIdForUpdate(orderId);
        if (order.isEmpty()) {
            log.error("Received {} for unknown order {}, ignored", routingKey, orderId);
        }
        return order;
    }

    /**
     * Redelivered and late events find the order in another status and are ignored.
     */
    private boolean expect(Order order, OrderStatus expected, String routingKey) {
        if (order.getStatus() == expected) {
            return true;
        }
        log.info("Order {} is {}, {} ignored", order.getOrderNumber(), order.getStatus(), routingKey);
        return false;
    }
}
