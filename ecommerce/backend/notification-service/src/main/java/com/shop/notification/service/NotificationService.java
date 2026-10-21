package com.shop.notification.service;

import com.shop.common.dto.PageResponse;
import com.shop.notification.dto.response.NotificationResponse;
import com.shop.notification.entity.NotificationStatus;
import com.shop.notification.entity.NotificationType;
import com.shop.notification.template.NotificationTemplates.RenderedNotification;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Records and delivers e-mails. Delivery happens outside database transactions; a failed delivery
 * is recorded and retried by a scheduled job.
 */
public interface NotificationService {

    /**
     * Saves the e-mail of an event and sends it. An event already handled (same event id) is ignored.
     */
    void notify(UUID eventId, RenderedNotification notification);

    /**
     * @return number of e-mails delivered by this run
     */
    int retryUndelivered();

    PageResponse<NotificationResponse> listOf(UUID userId, Pageable pageable);

    PageResponse<NotificationResponse> search(NotificationStatus status, NotificationType type, Pageable pageable);

    /**
     * Sends the e-mail again, whatever its status and number of attempts.
     */
    NotificationResponse resend(UUID notificationId);
}
