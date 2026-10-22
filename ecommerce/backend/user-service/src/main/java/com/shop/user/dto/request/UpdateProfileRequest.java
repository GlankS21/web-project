package com.shop.user.dto.request;

import com.shop.user.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(

        @Schema(description = "Имя", example = "Иван")
        @NotBlank @Size(max = 100)
        String firstName,

        @Schema(description = "Фамилия", example = "Петров")
        @NotBlank @Size(max = 100)
        String lastName,

        @Schema(description = "Телефон в формате +7XXXXXXXXXX", example = "+79161234567")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phoneNumber,

        @Schema(description = "Дата рождения", example = "1995-04-20")
        @Past
        LocalDate dateOfBirth
) {
}
