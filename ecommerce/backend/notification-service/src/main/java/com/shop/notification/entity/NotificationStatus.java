package com.shop.notification.entity;

/**
 * {@code PENDING} until the first delivery attempt. {@code FAILED} e-mails are retried until the
 * maximum number of attempts is reached; an administrator can resend them afterwards.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
