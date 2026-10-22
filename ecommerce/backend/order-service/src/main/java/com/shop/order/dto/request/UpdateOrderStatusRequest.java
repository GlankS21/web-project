package com.shop.order.dto.request;

import com.shop.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(

        @Schema(description = "SHIPPED (из PAID) или DELIVERED (из SHIPPED); для отмены используйте отдельный метод",
                example = "SHIPPED")
        @NotNull
        OrderStatus status,

        @Schema(description = "Комментарий в истории статусов", example = "Передан курьеру, трек-номер RA123456789RU")
        @Size(max = 255)
        String note
) {
}
