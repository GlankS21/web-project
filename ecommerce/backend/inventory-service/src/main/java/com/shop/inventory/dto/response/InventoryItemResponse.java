package com.shop.inventory.dto.response;

import java.time.Instant;

public record InventoryItemResponse(
        Long productId,
        String sku,
        int quantityOnHand,
        int quantityReserved,
        int availableQuantity,
        int lowStockThreshold,
        boolean lowStock,
        Instant updatedAt
) {
}
