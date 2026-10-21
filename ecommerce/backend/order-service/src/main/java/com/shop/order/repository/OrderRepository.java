package com.shop.order.repository;

import com.shop.order.entity.Order;
import com.shop.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    /**
     * Serialises every change of one order: saga replies, customer cancellation and expiry may
     * arrive at the same time. Collections load lazily afterwards.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") UUID orderId);

    Page<Order> findAllByUserId(UUID userId, Pageable pageable);

    Page<Order> findAllByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

    Optional<Order> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /**
     * Oldest orders still in one of {@code statuses} that were created before {@code before}.
     */
    @Query("""
            select o.id from CustomerOrder o
            where o.status in :statuses and o.createdAt < :before
            order by o.createdAt
            """)
    List<UUID> findIdsByStatusInAndCreatedAtBefore(@Param("statuses") Collection<OrderStatus> statuses,
                                                   @Param("before") Instant before,
                                                   Pageable pageable);
}
