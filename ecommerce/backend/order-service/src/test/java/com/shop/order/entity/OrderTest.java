package com.shop.order.entity;

import com.shop.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @Test
    void followsTheHappyPathAndRecordsTheTimeline() {
        Order order = placedOrder();

        order.changeStatus(OrderStatus.AWAITING_PAYMENT, "Stock reserved", null, NOW);
        order.changeStatus(OrderStatus.PAID, "Payment captured", null, NOW.plusSeconds(1));
        order.changeStatus(OrderStatus.SHIPPED, null, UUID.randomUUID(), NOW.plusSeconds(2));
        order.changeStatus(OrderStatus.DELIVERED, null, UUID.randomUUID(), NOW.plusSeconds(3));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaidAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(order.getDeliveredAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(order.getStatusHistory()).extracting(OrderStatusHistory::getToStatus).containsExactly(
                OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, OrderStatus.SHIPPED,
                OrderStatus.DELIVERED);
        assertThat(order.getStatusHistory().getFirst().getFromStatus()).isNull();
    }

    @Test
    void canBeCancelledOnlyBeforePayment() {
        assertThat(OrderStatus.PENDING.isCancellable()).isTrue();
        assertThat(OrderStatus.AWAITING_PAYMENT.isCancellable()).isTrue();
        assertThat(OrderStatus.PAID.isCancellable()).isFalse();
        assertThat(OrderStatus.CANCELLED.isCancellable()).isFalse();

        Order order = placedOrder();
        order.changeStatus(OrderStatus.CANCELLED, "x".repeat(600), null, NOW);
        assertThat(order.getCancelledAt()).isEqualTo(NOW);
        assertThat(order.getCancellationReason()).hasSize(Order.MAX_REASON_LENGTH);
    }

    @Test
    void rejectsSkippedOrBackwardTransitions() {
        Order order = placedOrder();

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.PAID, null, null, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot change from PENDING to PAID");
        order.changeStatus(OrderStatus.CANCELLED, "Out of stock", null, NOW);
        assertThatThrownBy(() -> order.changeStatus(OrderStatus.AWAITING_PAYMENT, null, null, NOW))
                .isInstanceOf(BusinessException.class);
        assertThat(order.getStatusHistory()).hasSize(2);
    }

    private Order placedOrder() {
        Order order = new Order();
        order.setOrderNumber("ORD-1");
        order.setUserId(UUID.randomUUID());
        order.markPlaced(NOW);
        return order;
    }
}
