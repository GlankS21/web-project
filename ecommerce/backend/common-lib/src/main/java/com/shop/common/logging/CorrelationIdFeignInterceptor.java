package com.shop.common.logging;

import com.shop.common.constant.HeaderConstants;
import com.shop.common.util.CorrelationIdUtils;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Propagates the current correlation id to outgoing Feign calls.
 */
public class CorrelationIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = CorrelationIdUtils.current();
        if (correlationId != null && !template.headers().containsKey(HeaderConstants.CORRELATION_ID)) {
            template.header(HeaderConstants.CORRELATION_ID, correlationId);
        }
    }
}
