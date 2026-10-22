package com.shop.product.dto.request;

import com.shop.product.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

        @Schema(example = "Смартфоны")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(description = "Если не указан, составляется из названия транслитерацией", example = "smartfony")
        @Size(max = 120)
        @Pattern(regexp = ValidationPatterns.SLUG, message = ValidationPatterns.SLUG_MESSAGE)
        String slug,

        @Schema(example = "Смартфоны на Android и iOS")
        @Size(max = 500)
        String description,

        @Schema(description = "Корневая категория, в которую вложена эта; не указывается для корневой категории. "
                + "Допускается только два уровня.")
        Long parentId,

        @Schema(description = "Позиция среди категорий того же уровня, по возрастанию", example = "1")
        @Min(0) @Max(10_000)
        Integer sortOrder
) {
}
