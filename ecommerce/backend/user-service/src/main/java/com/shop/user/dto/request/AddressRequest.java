package com.shop.user.dto.request;

import com.shop.user.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @Schema(description = "Имя и фамилия получателя", example = "Иван Петров")
        @NotBlank @Size(max = 150)
        String recipientName,

        @Schema(description = "Телефон получателя в формате +7XXXXXXXXXX", example = "+79161234567")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phoneNumber,

        @Schema(description = "Улица, дом, квартира", example = "ул. Тверская, д. 7, кв. 12")
        @NotBlank @Size(max = 255)
        String addressLine1,

        @Schema(description = "Дополнительно: подъезд, этаж, домофон", example = "подъезд 2, этаж 5")
        @Size(max = 255)
        String addressLine2,

        @Schema(description = "Район или муниципальный округ", example = "Тверской")
        @Size(max = 100)
        String ward,

        @Schema(description = "Административный округ или район области", example = "Центральный административный округ")
        @Size(max = 100)
        String district,

        @Schema(description = "Город или населённый пункт", example = "Москва")
        @NotBlank @Size(max = 100)
        String city,

        @Schema(description = "Почтовый индекс", example = "125009")
        @Size(max = 20)
        String postalCode,

        @Schema(description = "Код страны ISO 3166-1 alpha-2; если не указан - RU", example = "RU",
                defaultValue = "RU")
        @Pattern(regexp = ValidationPatterns.COUNTRY_CODE, message = ValidationPatterns.COUNTRY_CODE_MESSAGE)
        String countryCode,

        @Schema(description = "Сделать адресом доставки по умолчанию. Первый адрес всегда становится адресом по умолчанию.")
        Boolean defaultAddress
) {
}
