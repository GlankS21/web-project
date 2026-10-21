package com.shop.inventory.repository;

import com.shop.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    /**
     * Row locks ({@code SELECT ... FOR UPDATE}) taken in product id order, so that concurrent
     * reservations touching the same products always lock them in the same order (no deadlock).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId in :productIds order by i.productId")
    List<InventoryItem> findAllForUpdate(@Param("productIds") Collection<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId")
    Optional<InventoryItem> findForUpdate(@Param("productId") Long productId);

    List<InventoryItem> findAllByProductIdIn(Collection<Long> productIds);

    @Query("select i from InventoryItem i where i.quantityOnHand - i.quantityReserved <= i.lowStockThreshold")
    Page<InventoryItem> findLowStock(Pageable pageable);
}
