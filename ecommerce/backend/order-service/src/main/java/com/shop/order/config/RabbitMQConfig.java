package com.shop.order.config;

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
 * order-service publishes saga commands and outcomes on the order exchange and consumes the replies
 * of inventory-service and payment-service. Every queue has its dead-letter queue.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange orderExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange inventoryExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.INVENTORY_EXCHANGE);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.PAYMENT_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return RabbitTopology.deadLetterExchange();
    }

    @Bean
    public Declarables stockReservedQueue() {
        return consumerQueue(MessagingConstants.ORDER_STOCK_RESERVED_QUEUE,
                inventoryExchange(), MessagingConstants.STOCK_RESERVED_KEY);
    }

    @Bean
    public Declarables stockReservationFailedQueue() {
        return consumerQueue(MessagingConstants.ORDER_STOCK_RESERVATION_FAILED_QUEUE,
                inventoryExchange(), MessagingConstants.STOCK_RESERVATION_FAILED_KEY);
    }

    @Bean
    public Declarables paymentSucceededQueue() {
        return consumerQueue(MessagingConstants.ORDER_PAYMENT_SUCCEEDED_QUEUE,
                paymentExchange(), MessagingConstants.PAYMENT_SUCCEEDED_KEY);
    }

    @Bean
    public Declarables paymentFailedQueue() {
        return consumerQueue(MessagingConstants.ORDER_PAYMENT_FAILED_QUEUE,
                paymentExchange(), MessagingConstants.PAYMENT_FAILED_KEY);
    }

    @Bean
    public Declarables paymentRefundedQueue() {
        return consumerQueue(MessagingConstants.ORDER_PAYMENT_REFUNDED_QUEUE,
                paymentExchange(), MessagingConstants.PAYMENT_REFUNDED_KEY);
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
