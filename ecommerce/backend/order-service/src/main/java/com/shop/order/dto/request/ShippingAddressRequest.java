package com.shop.order.dto.request;

import com.shop.order.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Same rules as the address book of user-service; the client usually sends one of the saved addresses.
 */
public record ShippingAddressRequest(

        @Schema(example = "John Doe")
        @NotBlank @Size(max = 150)
        String recipientName,

        @Schema(example = "+84901234567")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phoneNumber,

        @Schema(example = "12 Nguyen Hue")
        @NotBlank @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @Schema(example = "Ben Nghe")
        @Size(max = 100)
        String ward,

        @Schema(example = "District 1")
        @Size(max = 100)
        String district,

        @Schema(example = "Ho Chi Minh City")
        @NotBlank @Size(max = 100)
        String city,

        @Size(max = 20)
        String postalCode,

        @Schema(example = "VN")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.COUNTRY_CODE, message = ValidationPatterns.COUNTRY_CODE_MESSAGE)
        String countryCode
) {
}
