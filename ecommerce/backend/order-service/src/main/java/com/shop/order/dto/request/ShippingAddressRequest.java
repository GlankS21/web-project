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

        @Schema(description = "Получатель", example = "Иван Петров")
        @NotBlank @Size(max = 150)
        String recipientName,

        @Schema(description = "Телефон получателя в формате +7XXXXXXXXXX", example = "+79161234567")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phoneNumber,

        @Schema(description = "Улица и дом", example = "ул. Тверская, д. 7")
        @NotBlank @Size(max = 255)
        String addressLine1,

        @Schema(description = "Квартира, подъезд, этаж", example = "кв. 12")
        @Size(max = 255)
        String addressLine2,

        @Schema(description = "Район", example = "Тверской")
        @Size(max = 100)
        String ward,

        @Schema(description = "Административный округ", example = "ЦАО")
        @Size(max = 100)
        String district,

        @Schema(description = "Город", example = "Москва")
        @NotBlank @Size(max = 100)
        String city,

        @Schema(description = "Почтовый индекс", example = "125009")
        @Size(max = 20)
        String postalCode,

        @Schema(description = "Код страны ISO 3166-1", example = "RU")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.COUNTRY_CODE, message = ValidationPatterns.COUNTRY_CODE_MESSAGE)
        String countryCode
) {
}
