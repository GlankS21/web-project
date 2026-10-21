package com.shop.notification.repository;

import com.shop.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID>,
        JpaSpecificationExecutor<Notification> {

    boolean existsByEventId(UUID eventId);

    Page<Notification> findAllByUserId(UUID userId, Pageable pageable);

    /**
     * E-mails to deliver again: failed ones with attempts left, and pending ones whose first attempt
     * never happened (the service stopped between saving and sending).
     */
    @Query("""
            select n.id from Notification n
            where (n.status = com.shop.notification.entity.NotificationStatus.FAILED and n.attempts < :maxAttempts)
               or (n.status = com.shop.notification.entity.NotificationStatus.PENDING and n.createdAt < :pendingBefore)
            order by n.createdAt
            """)
    List<UUID> findIdsToRetry(@Param("maxAttempts") int maxAttempts,
                              @Param("pendingBefore") Instant pendingBefore,
                              Pageable pageable);
}
