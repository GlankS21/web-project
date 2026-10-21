package com.shop.product.dto.response;

import com.shop.product.entity.ProductStatus;

import java.math.BigDecimal;

/**
 * Product card used in listings and by other services (cart, order) to read current prices.
 */
public record ProductSummaryResponse(
        Long id,
        String sku,
        String name,
        String slug,
        String shortDescription,
        String brand,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String currency,
        String thumbnailUrl,
        Long categoryId,
        String categoryName,
        ProductStatus status
) {
}
