package com.shop.notification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param mail        e-mail delivery
 * @param shopName    shown in subjects and signatures
 * @param retry       re-delivery of e-mails that could not be sent
 * @param frontendUrl base URL of the storefront used in e-mail links, without trailing slash
 */
@Validated
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        @Valid Mail mail,
        String shopName,
        @Valid Retry retry,
        String frontendUrl
) {

    public NotificationProperties {
        mail = mail == null ? new Mail(null, null) : mail;
        shopName = shopName == null || shopName.isBlank() ? "Shop" : shopName;
        retry = retry == null ? new Retry(null, null) : retry;
        frontendUrl = frontendUrl == null || frontendUrl.isBlank()
                ? "http://localhost:3000"
                : frontendUrl.trim().replaceAll("/+$", "");
    }

    /**
     * @param enabled {@code false} writes e-mails to the log instead of sending them
     * @param from    sender address
     */
    public record Mail(Boolean enabled, String from) {

        public Mail {
            enabled = enabled == null || enabled;
            from = from == null || from.isBlank() ? "no-reply@shop.local" : from;
        }
    }

    /**
     * @param maxAttempts delivery attempts per e-mail, the first one included
     * @param interval    delay between two retry runs
     */
    public record Retry(@Min(1) Integer maxAttempts, Duration interval) {

        public Retry {
            maxAttempts = maxAttempts == null ? 5 : maxAttempts;
            interval = interval == null ? Duration.ofMinutes(1) : interval;
        }
    }
}
