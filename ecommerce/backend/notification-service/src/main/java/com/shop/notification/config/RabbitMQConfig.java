package com.shop.notification.config;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.messaging.RabbitTopology;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * One queue per producing exchange, each bound to the routing keys that trigger an e-mail, with its
 * dead-letter queue. The listeners dispatch on the event type carried in the message headers.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange orderExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange userExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.USER_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return RabbitTopology.deadLetterExchange();
    }

    @Bean
    public Declarables orderEventsQueue() {
        return consumerQueue(MessagingConstants.NOTIFICATION_ORDER_EVENTS_QUEUE, orderExchange(),
                MessagingConstants.ORDER_CREATED_KEY,
                MessagingConstants.ORDER_CONFIRMED_KEY,
                MessagingConstants.ORDER_CANCELLED_KEY);
    }

    @Bean
    public Declarables userEventsQueue() {
        return consumerQueue(MessagingConstants.NOTIFICATION_USER_EVENTS_QUEUE, userExchange(),
                MessagingConstants.USER_REGISTERED_KEY);
    }

    private Declarables consumerQueue(String name, TopicExchange exchange, String... routingKeys) {
        Queue queue = RabbitTopology.queueWithDeadLetter(name);
        Queue deadLetterQueue = RabbitTopology.deadLetterQueue(name);
        List<Declarable> declarables = new ArrayList<>(List.of(queue, deadLetterQueue,
                RabbitTopology.deadLetterBinding(deadLetterQueue, deadLetterExchange())));
        for (String routingKey : routingKeys) {
            declarables.add(BindingBuilder.bind(queue).to(exchange).with(routingKey));
        }
        return new Declarables(declarables);
    }
}
