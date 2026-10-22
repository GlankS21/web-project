package com.shop.order.service.impl;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.PaymentFailedEvent;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.common.event.PaymentSucceededEvent;
import com.shop.common.event.StockReservationFailedEvent;
import com.shop.common.event.StockReservedEvent;
import com.shop.order.config.OrderProperties;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderStatus;
import com.shop.order.entity.PaymentMethod;
import com.shop.order.messaging.outbox.OutboxService;
import com.shop.order.repository.OrderRepository;
import com.shop.order.service.CouponLedger;
import com.shop.order.service.OrderLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private CouponLedger couponLedger;

    private OrderSagaServiceImpl service;
    private Order order;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        OrderProperties properties = new OrderProperties(null, null, null, Duration.ofMinutes(15), null, null, null);
        service = new OrderSagaServiceImpl(orderRepository, new OrderLifecycle(orderRepository, outboxService, couponLedger, clock,
                        new CommonMessagesAutoConfiguration().messageSource()),
                properties, clock);

        order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-1");
        order.setUserId(UUID.randomUUID());
        order.setUserEmail("ivan.petrov@example.ru");
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setTotalAmount(new BigDecimal("380000"));
        order.setCurrency("RUB");
        order.markPlaced(NOW.minusSeconds(5));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    @Test
    void stockReserved_requestsPaymentOnce() {
        service.onStockReserved(stockReserved());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        ArgumentCaptor<PaymentRequestedEvent> event = ArgumentCaptor.forClass(PaymentRequestedEvent.class);
        verify(outboxService).enqueue(eq(MessagingConstants.ORDER_EXCHANGE),
                eq(MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY), event.capture(), eq(order.getId()));
        assertThat(event.getValue().amount()).isEqualByComparingTo("380000");
        assertThat(event.getValue().paymentMethod()).isEqualTo("ONLINE");

        // Redelivery finds the order in AWAITING_PAYMENT: nothing more is published
        service.onStockReserved(stockReserved());
        verify(outboxService).enqueue(any(), any(), any(), any());
    }

    @Test
    void stockReservationFailed_cancelsTheOrder() {
        service.onStockReservationFailed(new StockReservationFailedEvent(UUID.randomUUID(), NOW, order.getId(),
                "ORD-1", "Недостаточно товара на складе: «Кабель USB-C» (заказано 1, в наличии 0)"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Недостаточно товара на складе: «Кабель USB-C» (заказано 1, в наличии 0)");
        verify(couponLedger).release(order);
        verify(outboxService).enqueue(eq(MessagingConstants.ORDER_EXCHANGE), eq(MessagingConstants.ORDER_CANCELLED_KEY),
                any(OrderCancelledEvent.class), eq(order.getId()));
    }

    @Test
    void paymentSucceeded_marksPaidAndConfirms() {
        order.changeStatus(OrderStatus.AWAITING_PAYMENT, null, null, NOW);
        UUID paymentId = UUID.randomUUID();

        service.onPaymentSucceeded(new PaymentSucceededEvent(UUID.randomUUID(), NOW, paymentId, order.getId(), "ORD-1",
                "TXN-1", new BigDecimal("380000"), "RUB"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo(paymentId);
        assertThat(order.getPaymentTransactionId()).isEqualTo("TXN-1");
        assertThat(order.getPaidAt()).isEqualTo(NOW);
        verify(outboxService).enqueue(eq(MessagingConstants.ORDER_EXCHANGE), eq(MessagingConstants.ORDER_CONFIRMED_KEY),
                any(OrderConfirmedEvent.class), eq(order.getId()));
    }

    @Test
    void paymentSucceededAfterCancellation_leavesTheOrderCancelled() {
        order.changeStatus(OrderStatus.CANCELLED, "Отменён покупателем", order.getUserId(), NOW);

        service.onPaymentSucceeded(new PaymentSucceededEvent(UUID.randomUUID(), NOW, UUID.randomUUID(), order.getId(),
                "ORD-1", "TXN-1", new BigDecimal("380000"), "RUB"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(outboxService);
    }

    @Test
    void paymentFailed_cancelsTheOrderToReleaseTheStock() {
        order.changeStatus(OrderStatus.AWAITING_PAYMENT, null, null, NOW);

        service.onPaymentFailed(new PaymentFailedEvent(UUID.randomUUID(), NOW, UUID.randomUUID(), order.getId(),
                "ORD-1", new BigDecimal("380000"), "RUB", "Банк-эмитент отклонил операцию"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Оплата не прошла: Банк-эмитент отклонил операцию");
        verify(couponLedger).release(order);
        verify(outboxService).enqueue(eq(MessagingConstants.ORDER_EXCHANGE), eq(MessagingConstants.ORDER_CANCELLED_KEY),
                any(OrderCancelledEvent.class), eq(order.getId()));
    }

    @Test
    void expireOrder_cancelsOnlyOrdersStillWaiting() {
        assertThat(service.expireOrder(order.getId())).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Заказ не был подтверждён вовремя (срок — 15 мин.)");

        // Already cancelled: nothing happens the second time
        assertThat(service.expireOrder(order.getId())).isFalse();
        verify(outboxService).enqueue(any(), any(), any(), any());
    }

    @Test
    void stockReservationFailedWithoutReason_usesTheDefaultReason() {
        service.onStockReservationFailed(new StockReservationFailedEvent(UUID.randomUUID(), NOW, order.getId(),
                "ORD-1", null));

        assertThat(order.getCancellationReason()).isEqualTo("Недостаточно товара на складе");
    }

    @Test
    void paymentFailedWithoutReason_usesTheDefaultReason() {
        order.changeStatus(OrderStatus.AWAITING_PAYMENT, null, null, NOW);

        service.onPaymentFailed(new PaymentFailedEvent(UUID.randomUUID(), NOW, UUID.randomUUID(), order.getId(),
                "ORD-1", new BigDecimal("380000"), "RUB", " "));

        assertThat(order.getCancellationReason()).isEqualTo("Оплата не прошла");
    }

    @Test
    void timelineNotesAreRussian() {
        service.onStockReserved(stockReserved());
        service.onPaymentSucceeded(new PaymentSucceededEvent(UUID.randomUUID(), NOW, UUID.randomUUID(),
                order.getId(), "ORD-1", "TXN-7", new BigDecimal("380000"), "RUB"));

        assertThat(order.getStatusHistory()).extracting(entry -> entry.getReason())
                .endsWith("Товар зарезервирован", "Оплата получена (транзакция TXN-7)");
    }

    private StockReservedEvent stockReserved() {
        return new StockReservedEvent(UUID.randomUUID(), NOW, order.getId(), "ORD-1");
    }
}
