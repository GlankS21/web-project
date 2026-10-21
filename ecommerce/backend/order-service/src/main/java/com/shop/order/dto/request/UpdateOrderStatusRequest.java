package com.shop.order.dto.request;

import com.shop.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(

        @Schema(description = "SHIPPED (from PAID) or DELIVERED (from SHIPPED); use the cancel endpoint to cancel",
                example = "SHIPPED")
        @NotNull
        OrderStatus status,

        @Schema(example = "Handed to the courier, tracking number VN123456")
        @Size(max = 255)
        String note
) {
}
