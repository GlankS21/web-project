package com.shop.common.logging;

import com.shop.common.constant.HeaderConstants;
import com.shop.common.util.CorrelationIdUtils;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;

/**
 * Stamps every outgoing RabbitMQ message with the current correlation id.
 */
public class CorrelationIdPublishPostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) throws AmqpException {
        message.getMessageProperties()
                .setHeader(HeaderConstants.CORRELATION_ID, CorrelationIdUtils.currentOrGenerate());
        return message;
    }
}
