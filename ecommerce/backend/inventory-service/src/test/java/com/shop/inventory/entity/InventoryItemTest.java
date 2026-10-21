package com.shop.inventory.entity;

import com.shop.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemTest {

    @Test
    void reserveHoldsUnitsWithoutRemovingThem() {
        InventoryItem item = item(10, 0);

        item.reserve(4);

        assertThat(item.getQuantityOnHand()).isEqualTo(10);
        assertThat(item.getQuantityReserved()).isEqualTo(4);
        assertThat(item.availableQuantity()).isEqualTo(6);
    }

    @Test
    void reserveRefusesMoreThanAvailable() {
        InventoryItem item = item(10, 8);

        assertThatThrownBy(() -> item.reserve(3)).isInstanceOf(BusinessException.class);
        assertThat(item.getQuantityReserved()).isEqualTo(8);
    }

    @Test
    void commitRemovesReservedUnitsFromStock() {
        InventoryItem item = item(10, 4);

        item.commit(4);

        assertThat(item.getQuantityOnHand()).isEqualTo(6);
        assertThat(item.getQuantityReserved()).isZero();
    }

    @Test
    void releaseMakesUnitsAvailableAgain() {
        InventoryItem item = item(10, 4);

        item.release(4);

        assertThat(item.availableQuantity()).isEqualTo(10);
    }

    @Test
    void releaseMoreThanReservedIsAProgrammingError() {
        assertThatThrownBy(() -> item(10, 1).release(2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adjustCannotRemoveReservedUnits() {
        InventoryItem item = item(10, 4);

        item.adjust(-6);
        assertThat(item.getQuantityOnHand()).isEqualTo(4);
        assertThatThrownBy(() -> item.adjust(-1)).isInstanceOf(BusinessException.class);
    }

    @Test
    void lowStockWhenAvailableAtOrBelowThreshold() {
        InventoryItem item = item(10, 5);
        item.setLowStockThreshold(5);

        assertThat(item.isLowStock()).isTrue();
        item.adjust(1);
        assertThat(item.isLowStock()).isFalse();
    }

    private InventoryItem item(int onHand, int reserved) {
        return InventoryItem.builder()
                .productId(1L)
                .quantityOnHand(onHand)
                .quantityReserved(reserved)
                .lowStockThreshold(2)
                .build();
    }
}
