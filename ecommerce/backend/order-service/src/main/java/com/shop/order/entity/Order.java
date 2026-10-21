package com.shop.order.entity;

import com.shop.common.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An order and its saga state. Status changes go through {@link #changeStatus}, which enforces the
 * transitions of {@link OrderStatus} and records the timeline.
 * <p>
 * The JPA entity name avoids the reserved JPQL keyword {@code ORDER}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order extends AuditableEntity {

    static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 30, updatable = false)
    private String orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_email", nullable = false, updatable = false)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal shippingFee;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(length = 500, updatable = false)
    private String note;

    /** Client-supplied key that makes checkout retries safe (unique per user). */
    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "cancellation_reason", length = MAX_REASON_LENGTH)
    private String cancellationReason;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "payment_transaction_id", length = 100)
    private String paymentTransactionId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @BatchSize(size = 50)
    @OrderBy("id ASC")
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OrderBy("id ASC")
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @Version
    private Long version;

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    /**
     * Starts the life cycle in {@link OrderStatus#PENDING}.
     */
    public void markPlaced(Instant at) {
        if (status != null) {
            throw new IllegalStateException("Order %s is already placed".formatted(orderNumber));
        }
        status = OrderStatus.PENDING;
        recordHistory(null, OrderStatus.PENDING, null, userId, at);
    }

    /**
     * @param reason  why the status changed (mandatory for cancellations)
     * @param actorId user who made the change, {@code null} for the saga and scheduled jobs
     * @throws BusinessException when the transition is not allowed
     */
    public void changeStatus(OrderStatus target, String reason, UUID actorId, Instant at) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException("Order %s cannot change from %s to %s".formatted(orderNumber, status, target));
        }
        OrderStatus previous = status;
        status = target;
        String truncatedReason = truncate(reason);
        switch (target) {
            case PAID -> paidAt = at;
            case SHIPPED -> shippedAt = at;
            case DELIVERED -> deliveredAt = at;
            case CANCELLED -> {
                cancelledAt = at;
                cancellationReason = truncatedReason;
            }
            case PENDING, AWAITING_PAYMENT -> {
                // no timestamp of its own
            }
        }
        recordHistory(previous, target, truncatedReason, actorId, at);
    }

    public boolean isOwnedBy(UUID candidateUserId) {
        return userId.equals(candidateUserId);
    }

    private void recordHistory(OrderStatus from, OrderStatus to, String reason, UUID actorId, Instant at) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setOrder(this);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setReason(reason);
        entry.setChangedBy(actorId);
        entry.setCreatedAt(at);
        statusHistory.add(entry);
    }

    static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
