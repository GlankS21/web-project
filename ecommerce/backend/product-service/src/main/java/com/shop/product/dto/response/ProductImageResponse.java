package com.shop.product.dto.response;

public record ProductImageResponse(
        Long id,
        String url,
        String altText,
        int sortOrder
) {
}
