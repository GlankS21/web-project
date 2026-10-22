package com.shop.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment of one order: its current state, while every attempt and refund is kept as a
 * {@link PaymentTransaction}. There is at most one row per order, which makes the handling of
 * redelivered requests and cancellations idempotent; its row lock serialises every change of the
 * payment and of its transactions. Amount and method are {@code null} for a
 * {@link PaymentStatus#CANCELLED} marker (order cancelled before payment was requested).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends AuditableEntity {

    static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false, length = 30, updatable = false)
    private String orderNumber;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** The latest charge attempt; the captured one once the payment succeeded. */
    @Column(name = "current_charge_id")
    private UUID currentChargeId;

    /** Initialises the payment widget for the current attempt while it waits for the customer. */
    @Column(name = "confirmation_token")
    private String confirmationToken;

    /** Number of charge attempts made at the provider. */
    @Column(nullable = false)
    private int attempts;

    /** Reference of the captured charge at the payment provider. */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    /** Code of the last decline or error, e.g. {@code insufficient_funds}. */
    @Column(name = "failure_reason", length = MAX_REASON_LENGTH)
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refund_transaction_id", length = 100)
    private String refundTransactionId;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Version
    private Long version;

    public boolean isCurrentCharge(PaymentTransaction charge) {
        return charge.getId().equals(currentChargeId);
    }

    public void setFailureReason(String reason) {
        failureReason = reason == null || reason.length() <= MAX_REASON_LENGTH
                ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
