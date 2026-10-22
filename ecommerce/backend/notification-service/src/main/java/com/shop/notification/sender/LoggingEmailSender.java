package com.shop.notification.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Used when {@code app.notification.mail.enabled=false}: e-mails are written to the log only.
 * The whole body is logged on purpose, so that a developer can follow links such as the password
 * reset link; this sender must therefore not be used in production.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.notification.mail", name = "enabled", havingValue = "false")
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("E-mail to {} (delivery disabled)\nSubject: {}\n{}", to, subject, body);
    }
}
