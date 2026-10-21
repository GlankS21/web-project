package com.shop.product.dto.request;

import com.shop.product.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductImageRequest(

        @Schema(example = "https://picsum.photos/seed/phone/800/800")
        @NotBlank @Size(max = 1000)
        @Pattern(regexp = ValidationPatterns.HTTP_URL, message = ValidationPatterns.HTTP_URL_MESSAGE)
        String url,

        @Size(max = 255)
        String altText
) {
}
