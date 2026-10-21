package com.shop.inventory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetStockRequest(

        @Schema(description = "Counted physical stock; cannot be lower than the reserved quantity", example = "42")
        @NotNull @Min(0) @Max(1_000_000)
        Integer quantityOnHand,

        @Schema(description = "Unchanged when omitted", example = "5")
        @Min(0) @Max(1_000_000)
        Integer lowStockThreshold,

        @Schema(example = "Stock count 2026-10")
        @Size(max = 255)
        String reason
) {
}
