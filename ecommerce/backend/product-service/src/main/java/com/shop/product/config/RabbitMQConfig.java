package com.shop.product.config;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.messaging.RabbitTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * product-service only publishes; inventory-service declares its own queue.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange productExchange() {
        return RabbitTopology.topicExchange(MessagingConstants.PRODUCT_EXCHANGE);
    }
}
