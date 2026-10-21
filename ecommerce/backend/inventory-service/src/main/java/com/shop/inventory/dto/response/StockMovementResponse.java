package com.shop.inventory.dto.response;

import com.shop.inventory.entity.MovementType;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        Long id,
        Long productId,
        MovementType type,
        int onHandChange,
        int reservedChange,
        int onHandAfter,
        int reservedAfter,
        String reference,
        String reason,
        UUID actorId,
        Instant createdAt
) {
}
