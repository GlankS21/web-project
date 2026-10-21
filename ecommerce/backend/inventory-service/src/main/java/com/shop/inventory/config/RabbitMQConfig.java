package com.shop.inventory.config;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.messaging.RabbitTopology;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * inventory-service publishes saga replies on the inventory exchange and consumes order events
 * (reserve / commit / release) and {@code product.created}. Every queue has its dead-letter queue.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange inventoryExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.INVENTORY_EXCHANGE);
    }

    @Bean
    public TopicExchange orderExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange productExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.PRODUCT_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return RabbitTopology.deadLetterExchange();
    }

    @Bean
    public Declarables orderCreatedQueue() {
        return consumerQueue(MessagingConstants.INVENTORY_ORDER_CREATED_QUEUE,
                orderExchange(), MessagingConstants.ORDER_CREATED_KEY);
    }

    @Bean
    public Declarables orderConfirmedQueue() {
        return consumerQueue(MessagingConstants.INVENTORY_ORDER_CONFIRMED_QUEUE,
                orderExchange(), MessagingConstants.ORDER_CONFIRMED_KEY);
    }

    @Bean
    public Declarables orderCancelledQueue() {
        return consumerQueue(MessagingConstants.INVENTORY_ORDER_CANCELLED_QUEUE,
                orderExchange(), MessagingConstants.ORDER_CANCELLED_KEY);
    }

    @Bean
    public Declarables productCreatedQueue() {
        return consumerQueue(MessagingConstants.INVENTORY_PRODUCT_CREATED_QUEUE,
                productExchange(), MessagingConstants.PRODUCT_CREATED_KEY);
    }

    private Declarables consumerQueue(String name, TopicExchange exchange, String routingKey) {
        Queue queue = RabbitTopology.queueWithDeadLetter(name);
        Queue deadLetterQueue = RabbitTopology.deadLetterQueue(name);
        return new Declarables(
                queue,
                deadLetterQueue,
                BindingBuilder.bind(queue).to(exchange).with(routingKey),
                RabbitTopology.deadLetterBinding(deadLetterQueue, deadLetterExchange()));
    }
}
