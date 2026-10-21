package com.shop.inventory.repository;

import com.shop.inventory.entity.StockReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    boolean existsByOrderId(UUID orderId);

    @EntityGraph(attributePaths = "items")
    Optional<StockReservation> findByOrderId(UUID orderId);

    /**
     * Serialises the saga steps of one order (confirm vs cancel). Items load lazily afterwards:
     * PostgreSQL cannot lock the nullable side of an outer join.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.orderId = :orderId")
    Optional<StockReservation> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
