package com.shop.order.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        String sku,
        String productName,
        String thumbnailUrl,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
}
