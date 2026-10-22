package com.shop.notification.messaging.consumer;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.PasswordResetRequestedEvent;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.notification.service.NotificationService;
import com.shop.notification.template.NotificationTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * One queue carries the account events; the handler is chosen from the event type in the message
 * headers. Welcomes newly registered customers and e-mails password reset links. Failures are retried
 * by the listener container and then dead-lettered to {@code notification.user-events.dlq}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = MessagingConstants.NOTIFICATION_USER_EVENTS_QUEUE)
public class UserEventsConsumer {

    private final NotificationService notificationService;
    private final NotificationTemplates templates;

    @RabbitHandler
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received {} (event {}) for account {}",
                MessagingConstants.USER_REGISTERED_KEY, event.eventId(), event.userId());
        notificationService.notify(event.eventId(), templates.welcome(event));
    }

    /**
     * The event carries the raw reset token: it is never logged.
     */
    @RabbitHandler
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        log.info("Received {} (event {}) for account {}",
                MessagingConstants.USER_PASSWORD_RESET_REQUESTED_KEY, event.eventId(), event.userId());
        notificationService.notify(event.eventId(), templates.passwordReset(event));
    }
}
