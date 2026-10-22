package com.shop.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetStockRequest(

        @Schema(description = "Фактический остаток по итогам пересчёта; не может быть меньше зарезервированного количества",
                example = "42")
        @NotNull @Min(0) @Max(1_000_000)
        Integer quantityOnHand,

        @Schema(description = "Порог «мало на складе»; если не указан, не меняется", example = "5")
        @Min(0) @Max(1_000_000)
        Integer lowStockThreshold,

        @Schema(description = "Причина; по умолчанию «Инвентаризация»", example = "Инвентаризация, октябрь 2026")
        @Size(max = 255)
        String reason
) {
}
