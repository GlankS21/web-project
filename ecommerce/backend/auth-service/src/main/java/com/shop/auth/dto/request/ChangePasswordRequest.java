package com.shop.auth.dto.request;

import com.shop.auth.util.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @Schema(description = "Текущий пароль", example = "Secret123")
        @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH)
        String currentPassword,

        @Schema(description = "Новый пароль: от 8 до 72 символов, хотя бы одна буква и одна цифра", example = "NewSecret456")
        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.PATTERN_MESSAGE)
        String newPassword
) {
}
