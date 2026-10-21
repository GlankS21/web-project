package com.shop.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(

        @Schema(description = "Units added (goods received) or removed (damaged, lost); must not be 0", example = "20")
        @NotNull @Min(-1_000_000) @Max(1_000_000)
        Integer quantityChange,

        @Schema(example = "Received purchase order PO-2026-001")
        @NotBlank @Size(max = 255)
        String reason
) {
}
