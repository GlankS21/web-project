package com.shop.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Only the product and the quantity: the price is always read from product-service.
 */
public record OrderItemRequest(

        @Schema(description = "Идентификатор товара", example = "7")
        @NotNull @Positive
        Long productId,

        @Schema(description = "Строки одного товара суммируются; общее количество одного товара ограничено (по умолчанию 99)",
                example = "1")
        @NotNull @Min(1)
        Integer quantity
) {
}
