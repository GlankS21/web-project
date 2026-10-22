package com.shop.inventory.entity;

import com.shop.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stock of one product. The primary key is the product id assigned by product-service.
 * <p>
 * {@code quantityOnHand} is the physical stock; {@code quantityReserved} is the part of it held for
 * orders whose payment is pending. Only the difference can be sold or removed.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_items")
public class InventoryItem extends AuditableEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    /** Filled from {@code product.created}; {@code null} for records created before the event arrived. */
    @Column(length = 64)
    private String sku;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    /** {@code null} until persisted, which lets Spring Data detect new entities with assigned ids. */
    @Version
    private Long version;

    public int availableQuantity() {
        return quantityOnHand - quantityReserved;
    }

    public boolean isLowStock() {
        return availableQuantity() <= lowStockThreshold;
    }

    public void reserve(int quantity) {
        requirePositive(quantity);
        if (quantity > availableQuantity()) {
            throw new BusinessException("inventory.error.insufficient-available", availableQuantity(), productId);
        }
        quantityReserved += quantity;
    }

    public void release(int quantity) {
        requirePositive(quantity);
        if (quantity > quantityReserved) {
            throw new IllegalStateException("Cannot release %d unit(s) of product %d, only %d reserved"
                    .formatted(quantity, productId, quantityReserved));
        }
        quantityReserved -= quantity;
    }

    /**
     * The reserved units leave the warehouse: both on-hand and reserved quantities decrease.
     */
    public void commit(int quantity) {
        release(quantity);
        quantityOnHand -= quantity;
    }

    /**
     * Units of a cancelled paid order go back to the stock.
     */
    public void restock(int quantity) {
        requirePositive(quantity);
        quantityOnHand += quantity;
    }

    /**
     * Manual correction; reserved units can never be removed.
     */
    public void adjust(int quantityChange) {
        int newOnHand = quantityOnHand + quantityChange;
        if (newOnHand < quantityReserved) {
            throw new BusinessException("inventory.error.below-reserved", productId, quantityReserved);
        }
        quantityOnHand = newOnHand;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
