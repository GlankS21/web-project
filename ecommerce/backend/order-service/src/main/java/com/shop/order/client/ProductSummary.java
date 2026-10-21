package com.shop.order.client;

import java.math.BigDecimal;

/**
 * The fields of product-service's {@code ProductSummaryResponse} that an order needs. Unknown fields
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
