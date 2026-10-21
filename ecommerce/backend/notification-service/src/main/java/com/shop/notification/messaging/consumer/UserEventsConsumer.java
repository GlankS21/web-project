package com.shop.notification.messaging.consumer;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.notification.service.NotificationService;
import com.shop.notification.template.NotificationTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Welcomes newly registered customers. Failures are retried by the listener container and then
 * dead-lettered to {@code notification.user-events.dlq}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventsConsumer {

    private final NotificationService notificationService;
    private final NotificationTemplates templates;

    @RabbitListener(queues = MessagingConstants.NOTIFICATION_USER_EVENTS_QUEUE)
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received {} (event {}) for account {}",
                MessagingConstants.USER_REGISTERED_KEY, event.eventId(), event.userId());
        notificationService.notify(event.eventId(), templates.welcome(event));
    }
}
