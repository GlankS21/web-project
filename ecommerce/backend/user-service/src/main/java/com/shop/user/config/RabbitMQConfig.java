package com.shop.user.config;

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
 * Queue consuming {@code user.registered} from the user exchange, with its dead-letter queue.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange userExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.USER_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return RabbitTopology.deadLetterExchange();
    }

    @Bean
    public Queue userRegisteredQueue() {
        return RabbitTopology.queueWithDeadLetter(MessagingConstants.USER_USER_REGISTERED_QUEUE);
    }

    @Bean
    public Queue userRegisteredDeadLetterQueue() {
        return RabbitTopology.deadLetterQueue(MessagingConstants.USER_USER_REGISTERED_QUEUE);
    }

    @Bean
    public Binding userRegisteredBinding() {
        return BindingBuilder.bind(userRegisteredQueue())
                .to(userExchange())
                .with(MessagingConstants.USER_REGISTERED_KEY);
    }

    @Bean
    public Binding userRegisteredDeadLetterBinding() {
        return RabbitTopology.deadLetterBinding(userRegisteredDeadLetterQueue(), deadLetterExchange());
    }
}
