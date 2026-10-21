package com.shop.product.messaging.publisher;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.ProductCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays domain events to RabbitMQ once the creating transaction has committed.
 * <p>
 * If the broker is unreachable the event is lost, which is tolerated: inventory-service creates a
 * missing stock record on the first stock adjustment of the product.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductCreated(ProductCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(MessagingConstants.PRODUCT_EXCHANGE,
                    MessagingConstants.PRODUCT_CREATED_KEY, event);
            log.info("Published {} for product {}", MessagingConstants.PRODUCT_CREATED_KEY, event.productId());
        } catch (AmqpException ex) {
            log.error("Could not publish {} for product {}", MessagingConstants.PRODUCT_CREATED_KEY,
                    event.productId(), ex);
        }
    }
}
