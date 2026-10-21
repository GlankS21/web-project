package com.shop.product.dto.request;

import com.shop.product.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

        @Schema(example = "Điện thoại")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(description = "Generated from the name when omitted", example = "dien-thoai")
        @Size(max = 120)
        @Pattern(regexp = ValidationPatterns.SLUG, message = ValidationPatterns.SLUG_MESSAGE)
        String slug,

        @Size(max = 500)
        String description,

        @Schema(description = "Root category to nest under; omit for a root category. Only two levels are allowed.")
        Long parentId,

        @Schema(description = "Position among siblings, ascending", example = "1")
        @Min(0) @Max(10_000)
        Integer sortOrder
) {
}
