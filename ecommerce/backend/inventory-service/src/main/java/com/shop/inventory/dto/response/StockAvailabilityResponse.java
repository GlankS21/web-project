package com.shop.inventory.dto.response;

/**
 * Public view of the stock of one product: what can still be ordered.
 */
public record StockAvailabilityResponse(
        Long productId,
        int availableQuantity,
        boolean inStock
) {
}
