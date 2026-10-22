package com.shop.auth.messaging.publisher;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.PasswordResetRequestedEvent;
import com.shop.common.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays domain events to RabbitMQ once the originating transaction has committed, so that no
 * event is ever published for an account or a reset token that was rolled back.
 * <p>
 * If the broker is unreachable the event is lost, which is tolerated here: user-service creates
 * a missing profile lazily on the first {@code GET /api/users/me}, and a lost reset link can be
 * requested again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            rabbitTemplate.convertAndSend(MessagingConstants.USER_EXCHANGE,
                    MessagingConstants.USER_REGISTERED_KEY, event);
            log.info("Published {} for account {}", MessagingConstants.USER_REGISTERED_KEY, event.userId());
        } catch (AmqpException ex) {
            log.error("Could not publish {} for account {}", MessagingConstants.USER_REGISTERED_KEY,
                    event.userId(), ex);
        }
    }

    /**
     * Neither the token nor the e-mail address is logged. If the broker is unreachable the e-mail is
     * not sent; the customer can request a new link once the cooldown has elapsed.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        try {
            rabbitTemplate.convertAndSend(MessagingConstants.USER_EXCHANGE,
                    MessagingConstants.USER_PASSWORD_RESET_REQUESTED_KEY, event);
            log.info("Published {} for account {}", MessagingConstants.USER_PASSWORD_RESET_REQUESTED_KEY,
                    event.userId());
        } catch (AmqpException ex) {
            log.error("Could not publish {} for account {}", MessagingConstants.USER_PASSWORD_RESET_REQUESTED_KEY,
                    event.userId(), ex);
        }
    }
}
