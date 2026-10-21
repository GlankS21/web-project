package com.shop.common.logging;

import com.shop.common.constant.HeaderConstants;
import com.shop.common.util.CorrelationIdUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void propagatesIncomingHeaderToMdcAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderConstants.CORRELATION_ID, "incoming-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInChain.set(CorrelationIdUtils.current());

        filter.doFilter(request, response, chain);

        assertThat(seenInChain.get()).isEqualTo("incoming-id");
        assertThat(response.getHeader(HeaderConstants.CORRELATION_ID)).isEqualTo("incoming-id");
        assertThat(CorrelationIdUtils.current()).isNull();
    }

    @Test
    void generatesIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInChain.set(CorrelationIdUtils.current());

        filter.doFilter(request, response, chain);

        assertThat(seenInChain.get()).isNotBlank();
        assertThat(response.getHeader(HeaderConstants.CORRELATION_ID)).isEqualTo(seenInChain.get());
    }
}
