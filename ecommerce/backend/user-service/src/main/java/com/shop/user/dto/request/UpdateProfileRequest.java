package com.shop.user.dto.request;

import com.shop.user.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(

        @Schema(example = "John")
        @NotBlank @Size(max = 100)
        String firstName,

        @Schema(example = "Doe")
        @NotBlank @Size(max = 100)
        String lastName,

        @Schema(example = "+84901234567")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phoneNumber,

        @Schema(example = "1995-04-20")
        @Past
        LocalDate dateOfBirth
) {
}
