package com.shop.common.logging;

import com.shop.common.constant.HeaderConstants;
import com.shop.common.util.CorrelationIdUtils;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;

/**
 * Binds the correlation id of an incoming RabbitMQ message to the MDC of the consumer thread
 * before the listener runs. Each delivery overwrites the previous value on that thread.
 */
public class CorrelationIdReceivePostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) throws AmqpException {
        Object header = message.getMessageProperties().getHeader(HeaderConstants.CORRELATION_ID);
        String incoming = header != null ? header.toString() : null;
        CorrelationIdUtils.bind(CorrelationIdUtils.resolveOrGenerate(incoming));
        return message;
    }
}
