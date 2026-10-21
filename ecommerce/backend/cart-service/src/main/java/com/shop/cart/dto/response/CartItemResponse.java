package com.shop.cart.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param unitPrice         current catalogue price, or the saved one when the catalogue is unreachable
 * @param priceChanged      the price differs from the one shown when the line was last changed
 * @param availableQuantity units that can still be ordered, {@code null} when unknown
 */
public record CartItemResponse(
        Long productId,
        String sku,
        String productName,
        String thumbnailUrl,
        BigDecimal unitPrice,
        String currency,
        int quantity,
        BigDecimal lineTotal,
        boolean priceChanged,
        Integer availableQuantity,
        CartItemStatus status,
        Instant addedAt
) {
}
