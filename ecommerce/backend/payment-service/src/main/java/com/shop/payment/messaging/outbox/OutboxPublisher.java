package com.shop.payment.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.event.DomainEvent;
import com.shop.common.util.CorrelationIdUtils;
import com.shop.payment.config.PaymentProperties;
import com.shop.payment.entity.OutboxEvent;
import com.shop.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Relays pending outbox rows to RabbitMQ, oldest first, waiting for the broker's publisher confirm
 * before marking a row as published. Delivery is at-least-once (a crash between the confirm and the
 * commit re-publishes the row); consumers de-duplicate on the event id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    static final long CONFIRM_TIMEOUT_MS = 5_000;
    private static final String EVENT_PACKAGE = "com.shop.common.event.";
    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentProperties properties;
    private final Clock clock;

    /**
     * @return number of events published. Stops at the first failure so that a broker outage does
     * not burn through the whole batch; the failed row is retried on the next run, keeping order.
     */
    @Transactional
    public int publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.lockNextPending(properties.outbox().batchSize());
        int published = 0;
        for (OutboxEvent event : pending) {
            try {
                publish(event);
                event.setPublishedAt(clock.instant());
                event.setLastError(null);
                published++;
            } catch (RuntimeException ex) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                log.warn("Could not publish outbox event {} ({}), attempt {}: {}",
                        event.getId(), event.getRoutingKey(), event.getAttempts(), ex.getMessage());
                break;
            }
        }
        return published;
    }

    @Transactional
    public int purgePublishedBefore(Instant before) {
        return outboxEventRepository.deletePublishedBefore(before);
    }

    private void publish(OutboxEvent outboxEvent) {
        DomainEvent event = deserialize(outboxEvent);
        String previousCorrelationId = CorrelationIdUtils.current();
        // The publish post-processor stamps the correlation id of the request that produced the event
        CorrelationIdUtils.bind(outboxEvent.getCorrelationId() != null
                ? outboxEvent.getCorrelationId() : CorrelationIdUtils.generate());
        try {
            rabbitTemplate.invoke(operations -> {
                operations.convertAndSend(outboxEvent.getExchange(), outboxEvent.getRoutingKey(), event);
                operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
                return null;
            });
            log.info("Published {} (event {}) for {}", outboxEvent.getRoutingKey(), outboxEvent.getId(),
                    outboxEvent.getAggregateId());
        } finally {
            if (previousCorrelationId != null) {
                CorrelationIdUtils.bind(previousCorrelationId);
            } else {
                CorrelationIdUtils.clear();
            }
        }
    }

    private DomainEvent deserialize(OutboxEvent outboxEvent) {
        String type = outboxEvent.getEventType();
        if (!type.startsWith(EVENT_PACKAGE)) {
            throw new IllegalStateException("Refusing to deserialise untrusted event type " + type);
        }
        try {
            Class<?> eventClass = Class.forName(type);
            return (DomainEvent) objectMapper.readValue(outboxEvent.getPayload(), eventClass);
        } catch (ReflectiveOperationException | IOException ex) {
            throw new IllegalStateException("Cannot deserialise outbox event " + outboxEvent.getId(), ex);
        }
    }

    private static String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
