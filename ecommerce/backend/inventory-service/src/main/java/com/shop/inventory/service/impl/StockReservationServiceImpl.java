package com.shop.inventory.service.impl;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.event.StockReleasedEvent;
import com.shop.common.event.StockReservationFailedEvent;
import com.shop.common.event.StockReservedEvent;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.inventory.dto.response.ReservationResponse;
import com.shop.inventory.entity.InventoryItem;
import com.shop.inventory.entity.MovementType;
import com.shop.inventory.entity.ReservationStatus;
import com.shop.inventory.entity.StockMovement;
import com.shop.inventory.entity.StockReservation;
import com.shop.inventory.entity.StockReservationItem;
import com.shop.inventory.mapper.InventoryMapper;
import com.shop.inventory.messaging.outbox.OutboxService;
import com.shop.inventory.repository.InventoryItemRepository;
import com.shop.inventory.repository.StockMovementRepository;
import com.shop.inventory.repository.StockReservationRepository;
import com.shop.inventory.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReservationServiceImpl implements StockReservationService {

    static final int MAX_REASON_LENGTH = 500;

    private final StockReservationRepository reservationRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OutboxService outboxService;
    private final InventoryMapper inventoryMapper;
    private final Clock clock;
    private final MessageSource messageSource;

    @Override
    @Transactional
    public void reserve(OrderCreatedEvent event) {
        if (reservationRepository.existsByOrderId(event.orderId())) {
            log.info("Order {} already has a reservation, {} ignored", event.orderNumber(),
                    MessagingConstants.ORDER_CREATED_KEY);
            return;
        }

        StockReservation reservation = new StockReservation();
        reservation.setOrderId(event.orderId());
        reservation.setOrderNumber(event.orderNumber());

        SortedMap<Long, Integer> requested = new TreeMap<>();
        String failure = collectRequestedQuantities(event.items(), requested);
        List<InventoryItem> items = failure == null
                ? inventoryItemRepository.findAllForUpdate(requested.keySet())
                : List.of();
        if (failure == null) {
            failure = findShortages(requested, items);
        }

        if (failure != null) {
            reservation.setStatus(ReservationStatus.FAILED);
            reservation.setFailureReason(truncate(failure));
            reservationRepository.save(reservation);
            outboxService.enqueue(MessagingConstants.INVENTORY_EXCHANGE, MessagingConstants.STOCK_RESERVATION_FAILED_KEY,
                    new StockReservationFailedEvent(UUID.randomUUID(), clock.instant(), event.orderId(),
                            event.orderNumber(), reservation.getFailureReason()),
                    event.orderId());
            log.warn("Stock reservation for order {} failed: {}", event.orderNumber(), failure);
            return;
        }

        Map<Long, InventoryItem> byProductId = items.stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));
        requested.forEach((productId, quantity) -> {
            InventoryItem item = byProductId.get(productId);
            item.reserve(quantity);
            reservation.addItem(productId, quantity);
            stockMovementRepository.save(StockMovement.of(item, MovementType.RESERVATION, 0, quantity,
                    event.orderNumber(), null, null));
        });
        reservation.setStatus(ReservationStatus.RESERVED);
        reservationRepository.save(reservation);
        outboxService.enqueue(MessagingConstants.INVENTORY_EXCHANGE, MessagingConstants.STOCK_RESERVED_KEY,
                new StockReservedEvent(UUID.randomUUID(), clock.instant(), event.orderId(), event.orderNumber()),
                event.orderId());
        log.info("Reserved stock for order {} ({} product(s))", event.orderNumber(), requested.size());
    }

    @Override
    @Transactional
    public void commit(OrderConfirmedEvent event) {
        StockReservation reservation = reservationRepository.findByOrderIdForUpdate(event.orderId()).orElse(null);
        if (reservation == null) {
            log.error("Order {} was confirmed but has no stock reservation; nothing to commit", event.orderNumber());
            return;
        }
        switch (reservation.getStatus()) {
            case RESERVED -> {
                applyToItems(reservation, MovementType.SALE, InventoryItem::commit);
                reservation.setStatus(ReservationStatus.COMMITTED);
                log.info("Committed stock reservation of order {}", event.orderNumber());
            }
            case COMMITTED -> log.info("Reservation of order {} already committed, event ignored", event.orderNumber());
            case FAILED, RELEASED, RETURNED -> log.error(
                    "Order {} was confirmed but its reservation is {}; stock not committed",
                    event.orderNumber(), reservation.getStatus());
        }
    }

    @Override
    @Transactional
    public void release(OrderCancelledEvent event) {
        StockReservation reservation = reservationRepository.findByOrderIdForUpdate(event.orderId()).orElse(null);
        if (reservation == null) {
            StockReservation marker = new StockReservation();
            marker.setOrderId(event.orderId());
            marker.setOrderNumber(event.orderNumber());
            marker.setStatus(ReservationStatus.RELEASED);
            marker.setFailureReason(truncate(StringUtils.hasText(event.reason())
                    ? message("inventory.reservation.cancelled-before-reserve-with-reason", event.reason())
                    : message("inventory.reservation.cancelled-before-reserve")));
            reservationRepository.save(marker);
            log.info("Order {} cancelled before any reservation; marker recorded", event.orderNumber());
            return;
        }
        switch (reservation.getStatus()) {
            case RESERVED -> {
                applyToItems(reservation, MovementType.RELEASE, InventoryItem::release);
                reservation.setStatus(ReservationStatus.RELEASED);
                reservation.setFailureReason(truncate(event.reason()));
                outboxService.enqueue(MessagingConstants.INVENTORY_EXCHANGE, MessagingConstants.STOCK_RELEASED_KEY,
                        new StockReleasedEvent(UUID.randomUUID(), clock.instant(), event.orderId(), event.orderNumber()),
                        event.orderId());
                log.info("Released stock reservation of order {}", event.orderNumber());
            }
            case COMMITTED -> {
                // Paid order cancelled before shipping: the goods never left, they are sellable again
                applyToItems(reservation, MovementType.RETURN, InventoryItem::restock);
                reservation.setStatus(ReservationStatus.RETURNED);
                reservation.setFailureReason(truncate(event.reason()));
                outboxService.enqueue(MessagingConstants.INVENTORY_EXCHANGE, MessagingConstants.STOCK_RELEASED_KEY,
                        new StockReleasedEvent(UUID.randomUUID(), clock.instant(), event.orderId(), event.orderNumber()),
                        event.orderId());
                log.info("Returned the sold stock of cancelled order {}", event.orderNumber());
            }
            case RELEASED, RETURNED, FAILED -> log.info("Reservation of order {} is {}, nothing to release",
                    event.orderNumber(), reservation.getStatus());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(UUID orderId) {
        return reservationRepository.findByOrderId(orderId)
                .map(inventoryMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("inventory.error.reservation-not-found", orderId));
    }

    /**
     * Sums duplicate lines of the same product. Returns a failure reason for a malformed order.
     * Failure reasons are in Russian: order-service shows them to the customer as the cancellation reason.
     */
    private String collectRequestedQuantities(List<OrderItemPayload> lines, SortedMap<Long, Integer> requested) {
        if (lines == null || lines.isEmpty()) {
            return message("inventory.reservation.error.no-items");
        }
        for (OrderItemPayload line : lines) {
            if (line == null || line.productId() == null || line.quantity() <= 0) {
                return message("inventory.reservation.error.invalid-item");
            }
            requested.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return null;
    }

    private String findShortages(Map<Long, Integer> requested, List<InventoryItem> items) {
        Map<Long, InventoryItem> byProductId = items.stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));
        List<String> shortages = new ArrayList<>();
        requested.forEach((productId, quantity) -> {
            InventoryItem item = byProductId.get(productId);
            int available = item == null ? 0 : item.availableQuantity();
            if (available < quantity) {
                shortages.add(message("inventory.reservation.shortage", productId, quantity, available));
            }
        });
        return shortages.isEmpty() ? null
                : message("inventory.reservation.error.insufficient-stock", String.join(", ", shortages));
    }

    /**
     * Locks the reserved products in id order, applies the operation and records the movement.
     */
    private void applyToItems(StockReservation reservation, MovementType type,
                              ObjIntConsumer<InventoryItem> operation) {
        Map<Long, Integer> quantities = reservation.getItems().stream()
                .collect(Collectors.toMap(StockReservationItem::getProductId, StockReservationItem::getQuantity));
        List<InventoryItem> items = inventoryItemRepository.findAllForUpdate(quantities.keySet());
        if (items.size() != quantities.size()) {
            throw new IllegalStateException("Stock records of reservation %s are missing".formatted(reservation.getId()));
        }
        for (InventoryItem item : items) {
            int quantity = quantities.get(item.getProductId());
            operation.accept(item, quantity);
            int onHandChange = switch (type) {
                case SALE -> -quantity;
                case RETURN -> quantity;
                default -> 0;
            };
            int reservedChange = type == MovementType.RETURN ? 0 : -quantity;
            stockMovementRepository.save(StockMovement.of(item, type, onHandChange, reservedChange,
                    reservation.getOrderNumber(), null, null));
        }
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
