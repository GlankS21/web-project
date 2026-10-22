package com.shop.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(

        @Schema(description = "Новое количество товара; чтобы убрать товар, удалите его из корзины", example = "2")
        @NotNull @Min(1)
        Integer quantity
) {
}
