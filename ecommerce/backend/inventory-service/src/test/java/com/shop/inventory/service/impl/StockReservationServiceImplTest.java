package com.shop.inventory.service.impl;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.event.StockReleasedEvent;
import com.shop.common.event.StockReservationFailedEvent;
import com.shop.common.event.StockReservedEvent;
import com.shop.inventory.entity.InventoryItem;
import com.shop.inventory.entity.MovementType;
import com.shop.inventory.entity.ReservationStatus;
import com.shop.inventory.entity.StockMovement;
import com.shop.inventory.entity.StockReservation;
import com.shop.inventory.mapper.InventoryMapperImpl;
import com.shop.inventory.messaging.outbox.OutboxService;
import com.shop.inventory.repository.InventoryItemRepository;
import com.shop.inventory.repository.StockMovementRepository;
import com.shop.inventory.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @Mock
    private StockReservationRepository reservationRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private OutboxService outboxService;

    private StockReservationServiceImpl service;
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StockReservationServiceImpl(reservationRepository, inventoryItemRepository,
                stockMovementRepository, outboxService, new InventoryMapperImpl(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reserve_holdsEveryItemAndRepliesStockReserved() {
        InventoryItem phone = item(1L, 10, 0);
        InventoryItem cable = item(2L, 5, 0);
        when(inventoryItemRepository.findAllForUpdate(Set.of(1L, 2L))).thenReturn(List.of(phone, cable));

        // Two lines of product 1 are summed
        service.reserve(orderCreated(line(1L, 2), line(2L, 5), line(1L, 1)));

        assertThat(phone.getQuantityReserved()).isEqualTo(3);
        assertThat(cable.getQuantityReserved()).isEqualTo(5);
        StockReservation saved = savedReservation();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(saved.getItems()).extracting(i -> i.getProductId() + "x" + i.getQuantity())
                .containsExactly("1x3", "2x5");
        verify(stockMovementRepository, times(2)).save(any(StockMovement.class));
        verify(outboxService).enqueue(eq(MessagingConstants.INVENTORY_EXCHANGE), eq(MessagingConstants.STOCK_RESERVED_KEY),
                any(StockReservedEvent.class), eq(orderId));
    }

    @Test
    void reserve_reservesNothingWhenOneItemIsShort() {
        InventoryItem phone = item(1L, 10, 0);
        InventoryItem laptop = item(2L, 3, 2);
        when(inventoryItemRepository.findAllForUpdate(Set.of(1L, 2L, 3L))).thenReturn(List.of(phone, laptop));

        service.reserve(orderCreated(line(1L, 2), line(2L, 2), line(3L, 1)));

        assertThat(phone.getQuantityReserved()).isZero();
        assertThat(laptop.getQuantityReserved()).isEqualTo(2);
        StockReservation saved = savedReservation();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(saved.getFailureReason())
                .contains("product 2 (requested 2, available 1)")
                .contains("product 3 (requested 1, available 0)")
                .doesNotContain("product 1 ");
        verify(stockMovementRepository, never()).save(any());

        ArgumentCaptor<StockReservationFailedEvent> event = ArgumentCaptor.forClass(StockReservationFailedEvent.class);
        verify(outboxService).enqueue(eq(MessagingConstants.INVENTORY_EXCHANGE),
                eq(MessagingConstants.STOCK_RESERVATION_FAILED_KEY), event.capture(), eq(orderId));
        assertThat(event.getValue().reason()).isEqualTo(saved.getFailureReason());
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void reserve_failsMalformedOrderWithoutTouchingStock() {
        service.reserve(orderCreated(line(1L, 0)));

        assertThat(savedReservation().getStatus()).isEqualTo(ReservationStatus.FAILED);
        verify(inventoryItemRepository, never()).findAllForUpdate(any());
        verify(outboxService).enqueue(anyString(), eq(MessagingConstants.STOCK_RESERVATION_FAILED_KEY), any(), any());
    }

    @Test
    void reserve_isIdempotentForRedeliveredEvents() {
        when(reservationRepository.existsByOrderId(orderId)).thenReturn(true);

        service.reserve(orderCreated(line(1L, 1)));

        verify(reservationRepository, never()).save(any());
        verifyNoInteractions(inventoryItemRepository, outboxService);
    }

    @Test
    void commit_removesReservedUnitsOnce() {
        StockReservation reservation = reservation(ReservationStatus.RESERVED, 1L, 3);
        InventoryItem phone = item(1L, 10, 3);
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(reservation));
        when(inventoryItemRepository.findAllForUpdate(Set.of(1L))).thenReturn(List.of(phone));

        service.commit(orderConfirmed());

        assertThat(phone.getQuantityOnHand()).isEqualTo(7);
        assertThat(phone.getQuantityReserved()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movement.capture());
        assertThat(movement.getValue().getType()).isEqualTo(MovementType.SALE);
        assertThat(movement.getValue().getOnHandChange()).isEqualTo(-3);
        assertThat(movement.getValue().getReservedChange()).isEqualTo(-3);

        // Redelivery: already committed
        service.commit(orderConfirmed());
        assertThat(phone.getQuantityOnHand()).isEqualTo(7);
    }

    @Test
    void release_returnsReservedUnitsAndRepliesStockReleased() {
        StockReservation reservation = reservation(ReservationStatus.RESERVED, 1L, 3);
        InventoryItem phone = item(1L, 10, 3);
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(reservation));
        when(inventoryItemRepository.findAllForUpdate(Set.of(1L))).thenReturn(List.of(phone));

        service.release(orderCancelled());

        assertThat(phone.getQuantityReserved()).isZero();
        assertThat(phone.getQuantityOnHand()).isEqualTo(10);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(outboxService).enqueue(eq(MessagingConstants.INVENTORY_EXCHANGE), eq(MessagingConstants.STOCK_RELEASED_KEY),
                any(StockReleasedEvent.class), eq(orderId));
    }

    @Test
    void release_ofFailedReservationDoesNothing() {
        when(reservationRepository.findByOrderIdForUpdate(orderId))
                .thenReturn(Optional.of(reservation(ReservationStatus.FAILED, 1L, 3)));

        service.release(orderCancelled());

        verifyNoInteractions(inventoryItemRepository, outboxService);
    }

    @Test
    void release_beforeReservationLeavesMarkerThatBlocksLateReservation() {
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty());

        service.release(orderCancelled());

        StockReservation marker = savedReservation();
        assertThat(marker.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(marker.getOrderId()).isEqualTo(orderId);
        assertThat(marker.getItems()).isEmpty();
        verifyNoInteractions(outboxService);
    }

    private StockReservation savedReservation() {
        ArgumentCaptor<StockReservation> captor = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservationRepository).save(captor.capture());
        return captor.getValue();
    }

    private StockReservation reservation(ReservationStatus status, Long productId, int quantity) {
        StockReservation reservation = new StockReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setOrderId(orderId);
        reservation.setOrderNumber("ORD-1");
        reservation.setStatus(status);
        reservation.addItem(productId, quantity);
        return reservation;
    }

    private InventoryItem item(Long productId, int onHand, int reserved) {
        return InventoryItem.builder()
                .productId(productId)
                .quantityOnHand(onHand)
                .quantityReserved(reserved)
                .lowStockThreshold(1)
                .build();
    }

    private OrderItemPayload line(Long productId, int quantity) {
        return new OrderItemPayload(productId, "SKU-" + productId, "Product " + productId, quantity, BigDecimal.TEN);
    }

    private OrderCreatedEvent orderCreated(OrderItemPayload... lines) {
        return new OrderCreatedEvent(UUID.randomUUID(), NOW, orderId, "ORD-1", UUID.randomUUID(), "jane@example.com",
                List.of(lines), BigDecimal.TEN, "VND");
    }

    private OrderConfirmedEvent orderConfirmed() {
        return new OrderConfirmedEvent(UUID.randomUUID(), NOW, orderId, "ORD-1", UUID.randomUUID(),
                "jane@example.com", BigDecimal.TEN, "VND");
    }

    private OrderCancelledEvent orderCancelled() {
        return new OrderCancelledEvent(UUID.randomUUID(), NOW, orderId, "ORD-1", UUID.randomUUID(),
                "jane@example.com", "Payment declined");
    }
}
