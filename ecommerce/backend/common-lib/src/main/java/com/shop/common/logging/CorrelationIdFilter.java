package com.shop.common.logging;

import com.shop.common.constant.HeaderConstants;
import com.shop.common.util.CorrelationIdUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds the {@code X-Correlation-Id} request header (or a generated id) to the MDC for the
 * duration of the request and echoes it back in the response.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationIdUtils.resolveOrGenerate(
                request.getHeader(HeaderConstants.CORRELATION_ID));
        CorrelationIdUtils.bind(correlationId);
        response.setHeader(HeaderConstants.CORRELATION_ID, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationIdUtils.clear();
        }
    }
}
