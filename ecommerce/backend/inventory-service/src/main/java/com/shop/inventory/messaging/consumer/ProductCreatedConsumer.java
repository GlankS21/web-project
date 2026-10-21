package com.shop.inventory.messaging.consumer;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.ProductCreatedEvent;
import com.shop.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Opens an empty stock record for every new product.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCreatedConsumer {

    private final InventoryService inventoryService;

    @RabbitListener(queues = MessagingConstants.INVENTORY_PRODUCT_CREATED_QUEUE)
    public void onProductCreated(ProductCreatedEvent event) {
        log.info("Received {} (event {}) for product {}",
                MessagingConstants.PRODUCT_CREATED_KEY, event.eventId(), event.productId());
        inventoryService.registerProduct(event);
    }
}
