package com.shop.payment.entity;

/**
 * {@code SUCCEEDED} payments become {@code REFUNDED} when their order is cancelled afterwards.
 * {@code CANCELLED} records an order cancelled before any charge, so that a late payment request
 * is never charged.
 */
public enum PaymentStatus {
    SUCCEEDED,
    FAILED,
    REFUNDED,
    CANCELLED
}
