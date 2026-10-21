package com.shop.product.dto.response;

import com.shop.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        Instant createdAt,
        Instant updatedAt
) {
}
