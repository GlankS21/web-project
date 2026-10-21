package com.shop.notification.sender;

/**
 * Delivers one plain-text e-mail. Throws on failure; the caller records the error and retries later.
 */
public interface EmailSender {

    void send(String to, String subject, String body);
}
