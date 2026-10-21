package com.shop.inventory.dto.response;

import com.shop.inventory.entity.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        ReservationStatus status,
        String failureReason,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt
) {

    public record Item(Long productId, int quantity) {
    }
}
