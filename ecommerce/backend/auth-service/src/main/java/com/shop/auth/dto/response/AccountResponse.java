package com.shop.auth.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String email,
        Set<String> roles,
        boolean enabled,
        Instant lastLoginAt,
        Instant createdAt
) {
}
