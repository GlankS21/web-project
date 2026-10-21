package com.shop.inventory.scheduler;

import com.shop.inventory.config.InventoryProperties;
import com.shop.inventory.messaging.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Drives the outbox relay. Safe on several instances: rows are claimed with {@code SKIP LOCKED}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final OutboxPublisher outboxPublisher;
    private final InventoryProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.inventory.outbox.poll-interval:1s}")
    public void relay() {
        try {
            int published;
            do {
                published = outboxPublisher.publishPending();
            } while (published == properties.outbox().batchSize());
        } catch (RuntimeException ex) {
            // e.g. database unavailable; the next run tries again
            log.error("Outbox relay run failed", ex);
        }
    }

    @Scheduled(cron = "${app.inventory.outbox.purge-cron:0 30 3 * * *}")
    public void purge() {
        int deleted = outboxPublisher.purgePublishedBefore(clock.instant().minus(properties.outbox().retention()));
        log.info("Outbox purge removed {} published event(s)", deleted);
    }
}
