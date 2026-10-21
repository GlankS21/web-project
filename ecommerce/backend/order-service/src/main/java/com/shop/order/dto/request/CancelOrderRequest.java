package com.shop.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(

        @Schema(example = "Ordered by mistake")
        @Size(max = 255)
        String reason
) {
}
