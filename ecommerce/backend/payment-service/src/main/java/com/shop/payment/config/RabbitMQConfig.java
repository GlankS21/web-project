package com.shop.payment.config;

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
 * payment-service publishes payment outcomes on the payment exchange and consumes payment requests and
 * cancellations from the order exchange. Every queue has its dead-letter queue.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange paymentExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.PAYMENT_EXCHANGE);
    }

    @Bean
    public TopicExchange orderExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.ORDER_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return RabbitTopology.deadLetterExchange();
    }

    @Bean
    public Declarables paymentRequestedQueue() {
        return consumerQueue(MessagingConstants.PAYMENT_PAYMENT_REQUESTED_QUEUE,
                orderExchange(), MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY);
    }

    @Bean
    public Declarables orderCancelledQueue() {
        return consumerQueue(MessagingConstants.PAYMENT_ORDER_CANCELLED_QUEUE,
                orderExchange(), MessagingConstants.ORDER_CANCELLED_KEY);
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
