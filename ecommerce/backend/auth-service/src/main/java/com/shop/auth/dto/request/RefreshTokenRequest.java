package com.shop.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code /refresh} and {@code /logout}.
 */
public record RefreshTokenRequest(

        @Schema(description = "Токен обновления, полученный при входе или предыдущем обновлении")
        @NotBlank @Size(max = 200)
        String refreshToken
) {
}
