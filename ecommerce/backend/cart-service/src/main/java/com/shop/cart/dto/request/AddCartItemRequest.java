package com.shop.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(

        @Schema(description = "Идентификатор товара", example = "7")
        @NotNull @Positive
        Long productId,

        @Schema(description = "Сколько штук добавить к количеству, уже лежащему в корзине", example = "1")
        @NotNull @Min(1)
        Integer quantity
) {
}
