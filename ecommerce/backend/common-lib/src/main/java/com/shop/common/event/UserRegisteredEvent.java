package com.shop.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * auth-service registered a new account. user-service creates the profile, notification-service greets the user.
 */
public record UserRegisteredEvent(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        String email,
        String firstName,
        String lastName
) implements DomainEvent {
}
