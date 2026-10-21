package com.shop.product.dto.request;

import com.shop.product.entity.ProductStatus;

import java.math.BigDecimal;

/**
 * Catalogue filters; every field is optional. {@code categoryId} includes the sub-categories of a
 * root category. {@code status} is honoured for administrators only, customers always see ACTIVE.
 */
public record ProductSearchCriteria(
        String keyword,
        Long categoryId,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductStatus status
) {
}
