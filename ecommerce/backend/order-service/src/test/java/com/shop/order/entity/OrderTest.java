package com.shop.order.entity;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private final MessageSource messageSource = new CommonMessagesAutoConfiguration().messageSource();

    @Test
    void followsTheHappyPathAndRecordsTheTimeline() {
        Order order = placedOrder();

        order.changeStatus(OrderStatus.AWAITING_PAYMENT, "Товар зарезервирован", null, NOW);
        order.changeStatus(OrderStatus.PAID, "Оплата получена", null, NOW.plusSeconds(1));
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
    void canBeCancelledOnlyBeforeShipping() {
        assertThat(OrderStatus.PENDING.isCancellable()).isTrue();
        assertThat(OrderStatus.AWAITING_PAYMENT.isCancellable()).isTrue();
        assertThat(OrderStatus.PAID.isCancellable()).isTrue();
        assertThat(OrderStatus.SHIPPED.isCancellable()).isFalse();
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
                .hasMessage("order.error.invalid-transition")
                .satisfies(ex -> assertThat(messageSource.getMessage(ex.getMessage(),
                        ((BusinessException) ex).getMessageArgs(), CommonMessagesAutoConfiguration.DEFAULT_LOCALE))
                        .isEqualTo("Заказ ORD-1 нельзя перевести из статуса «Ожидает подтверждения» в статус «Оплачен»"));
        order.changeStatus(OrderStatus.CANCELLED, "Недостаточно товара на складе", null, NOW);
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
