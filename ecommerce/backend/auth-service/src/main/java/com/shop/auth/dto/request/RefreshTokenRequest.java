package com.shop.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code /refresh} and {@code /logout}.
 */
public record RefreshTokenRequest(

        @NotBlank @Size(max = 200)
        String refreshToken
) {
}
