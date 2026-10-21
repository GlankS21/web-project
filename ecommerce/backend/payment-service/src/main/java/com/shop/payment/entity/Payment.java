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
 * Payment of one order. There is at most one row per order, which makes the handling of
 * redelivered requests and cancellations idempotent. Amount and method are {@code null} for a
 * {@link PaymentStatus#CANCELLED} marker (order cancelled before payment was requested).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends AuditableEntity {

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
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** Reference of the charge at the payment gateway. */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "refund_transaction_id", length = 100)
    private String refundTransactionId;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Version
    private Long version;
}
