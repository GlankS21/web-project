package com.shop.product.dto.request;

import com.shop.product.entity.ProductStatus;
import com.shop.product.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(

        @Schema(description = "Immutable after creation: an update must send the current SKU", example = "PHN-IP15-128")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.SKU, message = ValidationPatterns.SKU_MESSAGE)
        String sku,

        @Schema(example = "iPhone 15 128GB")
        @NotBlank @Size(max = 200)
        String name,

        @Schema(description = "Generated from the name when omitted", example = "iphone-15-128gb")
        @Size(max = 220)
        @Pattern(regexp = ValidationPatterns.SLUG, message = ValidationPatterns.SLUG_MESSAGE)
        String slug,

        @Size(max = 500)
        String shortDescription,

        @Size(max = 10_000)
        String description,

        @Schema(example = "Apple")
        @Size(max = 100)
        String brand,

        @Schema(example = "19990000")
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 12, fraction = 2)
        BigDecimal price,

        @Schema(description = "Previous price shown struck through; must be greater than price", example = "22990000")
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 12, fraction = 2)
        BigDecimal compareAtPrice,

        @Schema(example = "2")
        @NotNull
        Long categoryId,

        @Schema(description = "Defaults to DRAFT on creation; unchanged on update when omitted")
        ProductStatus status,

        @Schema(description = "Ordered list, the first image is the thumbnail. Replaces all images on update.")
        @Size(max = 10)
        List<@Valid @NotNull ProductImageRequest> images
) {
}
