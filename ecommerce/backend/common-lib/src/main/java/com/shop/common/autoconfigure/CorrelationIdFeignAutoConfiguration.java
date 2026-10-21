package com.shop.common.autoconfigure;

import com.shop.common.logging.CorrelationIdFeignInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Adds a global Feign {@code RequestInterceptor} that forwards the correlation id.
 */
@AutoConfiguration
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class CorrelationIdFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFeignInterceptor correlationIdFeignInterceptor() {
        return new CorrelationIdFeignInterceptor();
    }
}
