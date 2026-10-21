package com.shop.payment.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.event.DomainEvent;
import com.shop.common.util.CorrelationIdUtils;
import com.shop.payment.entity.OutboxEvent;
import com.shop.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Writes events to the outbox table. Must join the transaction that changes the state the event
 * describes: both are committed, or neither is.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String exchange, String routingKey, DomainEvent event, Object aggregateId) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(event.eventId());
        outboxEvent.setAggregateId(String.valueOf(aggregateId));
        outboxEvent.setExchange(exchange);
        outboxEvent.setRoutingKey(routingKey);
        outboxEvent.setEventType(event.getClass().getName());
        outboxEvent.setPayload(toJson(event));
        outboxEvent.setCorrelationId(CorrelationIdUtils.current());
        outboxEvent.setCreatedAt(clock.instant());
        outboxEventRepository.save(outboxEvent);
    }

    private String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise " + event.getClass().getSimpleName(), ex);
        }
    }
}
