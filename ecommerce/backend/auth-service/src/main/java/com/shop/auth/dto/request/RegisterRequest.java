package com.shop.auth.dto.request;

import com.shop.auth.util.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @Schema(example = "john.doe@example.com")
        @NotBlank @Email @Size(max = 255)
        String email,

        @Schema(example = "Secret123")
        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.PATTERN_MESSAGE)
        String password,

        @Schema(example = "John")
        @NotBlank @Size(max = 100)
        String firstName,

        @Schema(example = "Doe")
        @NotBlank @Size(max = 100)
        String lastName
) {
}
