package com.shop.product.dto.response;

import com.shop.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code ratingAverage} is the mean review rating rounded to one decimal (0 without reviews).
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String slug,
        String shortDescription,
        String description,
        String brand,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String currency,
        ProductStatus status,
        CategorySummaryResponse category,
        List<ProductImageResponse> images,
        BigDecimal ratingAverage,
        int ratingCount,
        Instant createdAt,
        Instant updatedAt
) {
}
