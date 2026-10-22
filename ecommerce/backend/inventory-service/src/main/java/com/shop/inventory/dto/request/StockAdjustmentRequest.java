package com.shop.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(

        @Schema(description = "Сколько единиц добавить (поступление) или списать (брак, потеря); не может быть 0",
                example = "20")
        @NotNull @Min(-1_000_000) @Max(1_000_000)
        Integer quantityChange,

        @Schema(description = "Причина корректировки", example = "Поступление по заказу поставщику PO-2026-001")
        @NotBlank @Size(max = 255)
        String reason
) {
}
