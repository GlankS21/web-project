package com.shop.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Event stored in the same transaction as the state change it describes (transactional outbox),
 * so that a saga reply is never lost when the broker is unavailable at commit time.
 * The id is the event id, which consumers use for de-duplication.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "outbox_events")
public class OutboxEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, length = 100, updatable = false)
    private String aggregateId;

    @Column(nullable = false, length = 255, updatable = false)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 255, updatable = false)
    private String routingKey;

    /** Fully qualified class name of the event record. */
    @Column(name = "event_type", nullable = false, length = 255, updatable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String payload;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Transient
    private boolean persisted;

    /** The id is assigned, so tell Spring Data to persist (not merge) new rows. */
    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }
}
