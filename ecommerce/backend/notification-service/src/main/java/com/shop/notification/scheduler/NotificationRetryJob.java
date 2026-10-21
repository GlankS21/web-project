package com.shop.notification.scheduler;

import com.shop.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delivers e-mails that failed (SMTP server down) or were never attempted, until the configured
 * maximum number of attempts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryJob {

    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.notification.retry.interval:1m}")
    public void retry() {
        try {
            int delivered = notificationService.retryUndelivered();
            if (delivered > 0) {
                log.info("Retry run delivered {} e-mail(s)", delivered);
            }
        } catch (RuntimeException ex) {
            // e.g. database unavailable; the next run tries again
            log.error("Notification retry run failed", ex);
        }
    }
}
