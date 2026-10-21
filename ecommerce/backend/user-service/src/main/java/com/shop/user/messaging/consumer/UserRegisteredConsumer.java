package com.shop.user.messaging.consumer;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Creates the profile of a newly registered account. Failures are retried by the listener
 * container (see shared config) and then dead-lettered to {@code user.user-registered.dlq}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final UserProfileService userProfileService;

    @RabbitListener(queues = MessagingConstants.USER_USER_REGISTERED_QUEUE)
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received {} (event {}) for account {}",
                MessagingConstants.USER_REGISTERED_KEY, event.eventId(), event.userId());
        userProfileService.createProfileFromRegistration(event);
    }
}
