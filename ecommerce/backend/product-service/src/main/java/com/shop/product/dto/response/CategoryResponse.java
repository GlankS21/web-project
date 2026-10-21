package com.shop.product.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code children} is filled for root categories in the tree and detail views, empty otherwise.
 */
public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        Long parentId,
        int sortOrder,
        List<CategoryResponse> children,
        Instant createdAt,
        Instant updatedAt
) {
}
