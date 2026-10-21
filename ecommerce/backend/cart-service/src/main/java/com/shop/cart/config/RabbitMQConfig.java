package com.shop.cart.config;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.messaging.RabbitTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queue consuming {@code order.created} (ordered products leave the cart), with its dead-letter queue.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange orderExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.ORDER_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return RabbitTopology.deadLetterExchange();
    }

    @Bean
    public Queue orderCreatedQueue() {
        return RabbitTopology.queueWithDeadLetter(MessagingConstants.CART_ORDER_CREATED_QUEUE);
    }

    @Bean
    public Queue orderCreatedDeadLetterQueue() {
        return RabbitTopology.deadLetterQueue(MessagingConstants.CART_ORDER_CREATED_QUEUE);
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue())
                .to(orderExchange())
                .with(MessagingConstants.ORDER_CREATED_KEY);
    }

    @Bean
    public Binding orderCreatedDeadLetterBinding() {
        return RabbitTopology.deadLetterBinding(orderCreatedDeadLetterQueue(), deadLetterExchange());
    }
}
