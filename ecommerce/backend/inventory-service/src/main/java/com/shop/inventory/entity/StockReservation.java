package com.shop.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stock held for one order. There is at most one reservation per order, which makes every saga
 * step idempotent under redelivery.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stock_reservations")
public class StockReservation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @OrderBy("productId ASC")
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockReservationItem> items = new ArrayList<>();

    @Version
    private Long version;

    public void addItem(Long productId, int quantity) {
        StockReservationItem item = new StockReservationItem();
        item.setReservation(this);
        item.setProductId(productId);
        item.setQuantity(quantity);
        items.add(item);
    }
}
