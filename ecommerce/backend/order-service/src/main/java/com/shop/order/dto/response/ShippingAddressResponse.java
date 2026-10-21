package com.shop.order.dto.response;

public record ShippingAddressResponse(
        String recipientName,
        String phoneNumber,
        String addressLine1,
        String addressLine2,
        String ward,
        String district,
        String city,
        String postalCode,
        String countryCode
) {
}
