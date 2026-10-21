package com.shop.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable ledger entry: how one operation changed the stock of a product, and the result.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stock_movements")
@EntityListeners(AuditingEntityListener.class)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private MovementType type;

    @Column(name = "on_hand_change", nullable = false, updatable = false)
    private int onHandChange;

    @Column(name = "reserved_change", nullable = false, updatable = false)
    private int reservedChange;

    @Column(name = "on_hand_after", nullable = false, updatable = false)
    private int onHandAfter;

    @Column(name = "reserved_after", nullable = false, updatable = false)
    private int reservedAfter;

    /** Order number for saga movements. */
    @Column(length = 100, updatable = false)
    private String reference;

    @Column(length = 255, updatable = false)
    private String reason;

    /** Administrator who made a manual adjustment. */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Ledger entry for a change already applied to {@code item}; the "after" values are read from it.
     */
    public static StockMovement of(InventoryItem item, MovementType type, int onHandChange, int reservedChange,
                                   String reference, String reason, UUID actorId) {
        StockMovement movement = new StockMovement();
        movement.setProductId(item.getProductId());
        movement.setType(type);
        movement.setOnHandChange(onHandChange);
        movement.setReservedChange(reservedChange);
        movement.setOnHandAfter(item.getQuantityOnHand());
        movement.setReservedAfter(item.getQuantityReserved());
        movement.setReference(reference);
        movement.setReason(reason);
        movement.setActorId(actorId);
        return movement;
    }
}
