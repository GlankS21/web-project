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

        @Schema(description = "Артикул; после создания не меняется, при изменении товара передаётся текущий",
                example = "PHN-IP15-128")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.SKU, message = ValidationPatterns.SKU_MESSAGE)
        String sku,

        @Schema(example = "Смартфон Apple iPhone 15 128 ГБ")
        @NotBlank @Size(max = 200)
        String name,

        @Schema(description = "Если не указан, составляется из названия транслитерацией",
                example = "smartfon-apple-iphone-15-128-gb")
        @Size(max = 220)
        @Pattern(regexp = ValidationPatterns.SLUG, message = ValidationPatterns.SLUG_MESSAGE)
        String slug,

        @Schema(example = "Чип A16 Bionic, камера 48 Мп, Dynamic Island")
        @Size(max = 500)
        String shortDescription,

        @Schema(example = "Дисплей Super Retina XDR 6,1 дюйма, разъём USB-C, аккумулятора хватает на весь день.")
        @Size(max = 10_000)
        String description,

        @Schema(example = "Apple")
        @Size(max = 100)
        String brand,

        @Schema(description = "Цена в рублях", example = "66690")
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 12, fraction = 2)
        BigDecimal price,

        @Schema(description = "Старая цена, показывается зачёркнутой; должна быть больше цены", example = "76690")
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 12, fraction = 2)
        BigDecimal compareAtPrice,

        @Schema(example = "2")
        @NotNull
        Long categoryId,

        @Schema(description = "При создании по умолчанию DRAFT; при изменении, если не указан, не меняется")
        ProductStatus status,

        @Schema(description = "Упорядоченный список, первое изображение — миниатюра. При изменении товара "
                + "заменяет все изображения.")
        @Size(max = 10)
        List<@Valid @NotNull ProductImageRequest> images
) {
}
