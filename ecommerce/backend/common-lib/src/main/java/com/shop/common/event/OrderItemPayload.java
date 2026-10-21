package com.shop.common.event;

import java.math.BigDecimal;

/**
 * Line item carried inside order events.
 */
public record OrderItemPayload(
        Long productId,
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
}
