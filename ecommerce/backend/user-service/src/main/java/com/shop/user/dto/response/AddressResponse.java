package com.shop.user.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String recipientName,
        String phoneNumber,
        String addressLine1,
        String addressLine2,
        String ward,
        String district,
        String city,
        String postalCode,
        String countryCode,
        boolean defaultAddress,
        Instant createdAt,
        Instant updatedAt
) {
}
