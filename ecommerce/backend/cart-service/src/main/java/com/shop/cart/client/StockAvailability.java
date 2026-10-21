package com.shop.cart.client;

/**
 * inventory-service's {@code StockAvailabilityResponse}.
 */
public record StockAvailability(
        Long productId,
        int availableQuantity,
        boolean inStock
) {
}
