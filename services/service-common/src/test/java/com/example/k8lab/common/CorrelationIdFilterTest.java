package com.example.k8lab.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesSafeIdAndClearsItAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertEquals("request-123", MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertEquals("request-123", response.getHeader(CorrelationIdFilter.HEADER_NAME));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void replacesInvalidIdWithGeneratedSafeId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "invalid id\r\n");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertTrue(MDC.get(CorrelationIdFilter.MDC_KEY).matches("[A-Za-z0-9._-]{1,64}")));

        String correlationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertNotNull(correlationId);
        assertTrue(correlationId.matches("[A-Za-z0-9._-]{1,64}"));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void restoresExistingMdcValueAfterRequest() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "outer-request");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "inner-request");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    assertEquals("inner-request", MDC.get(CorrelationIdFilter.MDC_KEY)));

            assertEquals("outer-request", MDC.get(CorrelationIdFilter.MDC_KEY));
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
