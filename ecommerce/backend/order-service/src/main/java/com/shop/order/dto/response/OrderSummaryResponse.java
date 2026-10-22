package com.shop.order.dto.response;

import com.shop.order.entity.OrderStatus;
import com.shop.order.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Row of the order history and of the administration list.
 */
public record OrderSummaryResponse(
        UUID id,
        String orderNumber,
        UUID userId,
        String userEmail,
        OrderStatus status,
        PaymentMethod paymentMethod,
        String couponCode,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String currency,
        int itemCount,
        String firstItemName,
        String firstItemThumbnailUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
