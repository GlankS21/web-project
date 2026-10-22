package com.shop.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @Schema(description = "E-mail аккаунта", example = "ivan.petrov@example.ru")
        @NotBlank @Email @Size(max = 255)
        String email,

        @Schema(description = "Пароль", example = "Secret123")
        @NotBlank @Size(max = 72)
        String password
) {
}
