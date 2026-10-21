package com.shop.auth.config;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.messaging.RabbitTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * auth-service only publishes; consumers (user-service, notification-service) declare their queues.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange userExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.USER_EXCHANGE);
    }
}
