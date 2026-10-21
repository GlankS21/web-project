package com.shop.notification.dto.response;

import com.shop.notification.entity.NotificationStatus;
import com.shop.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        UUID userId,
        String recipient,
        String reference,
        String subject,
        String body,
        NotificationStatus status,
        int attempts,
        String lastError,
        Instant sentAt,
        Instant createdAt
) {
}
