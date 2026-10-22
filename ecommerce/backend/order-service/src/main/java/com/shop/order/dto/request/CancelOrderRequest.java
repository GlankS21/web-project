package com.shop.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(

        @Schema(description = "Причина отмены", example = "Заказал по ошибке")
        @Size(max = 255)
        String reason
) {
}
