package com.shop.product.dto.request;

import com.shop.product.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductImageRequest(

        @Schema(description = "Абсолютный адрес http(s) или адрес, полученный от POST /api/products/images",
                example = "https://picsum.photos/seed/phone/800/800")
        @NotBlank @Size(max = 1000)
        @Pattern(regexp = ValidationPatterns.IMAGE_URL, message = ValidationPatterns.IMAGE_URL_MESSAGE)
        String url,

        @Schema(description = "Альтернативный текст изображения", example = "Смартфон Apple iPhone 15 128 ГБ, вид спереди")
        @Size(max = 255)
        String altText
) {
}
