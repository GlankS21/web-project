package com.shop.common.messaging;

import com.shop.common.constant.MessagingConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * Factory methods that keep exchange/queue declarations identical across services.
 * RabbitMQ rejects re-declaration with different arguments, so every service must use these.
 */
public final class RabbitTopology {

    private RabbitTopology() {
    }

    public static TopicExchange topicExchange(String name) {
        return ExchangeBuilder.topicExchange(name).durable(true).build();
    }

    public static DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(MessagingConstants.DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    /**
     * Durable queue whose rejected messages (after listener retries are exhausted) go to its DLQ.
     */
    public static Queue queueWithDeadLetter(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(MessagingConstants.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterQueueName(name))
                .build();
    }

    public static Queue deadLetterQueue(String name) {
        return QueueBuilder.durable(deadLetterQueueName(name)).build();
    }

    public static Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(deadLetterQueue.getName());
    }

    public static String deadLetterQueueName(String name) {
        return name + MessagingConstants.DEAD_LETTER_SUFFIX;
    }
}
