package com.shop.cart.client;

import java.math.BigDecimal;

/**
 * The fields of product-service's {@code ProductSummaryResponse} that a cart needs. Unknown fields
 * are ignored (see the shared Jackson configuration).
 */
public record ProductSummary(
        Long id,
        String sku,
        String name,
        BigDecimal price,
        String currency,
        String thumbnailUrl,
        String status
) {
}
