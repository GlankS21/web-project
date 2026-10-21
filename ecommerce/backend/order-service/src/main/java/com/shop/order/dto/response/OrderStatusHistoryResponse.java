package com.shop.order.dto.response;

import com.shop.order.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusHistoryResponse(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String reason,
        UUID changedBy,
        Instant createdAt
) {
}
