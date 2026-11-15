package com.example.k8lab.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

class BearerTokenFilterTest {
    private static final String USER_ID = "9ac7e218-1374-4c93-8803-cb9aaee2832a";
    private final TokenService tokens =
            new TokenService("local-development-secret-with-at-least-32-bytes");
    private final BearerTokenFilter filter = new BearerTokenFilter(tokens);

    @Test
    void rejectsCartRequestWithoutBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void attachesVerifiedUserIdToCartRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("Authorization", "Bearer " + tokens.issue(USER_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(USER_ID, request.getAttribute(BearerTokenFilter.USER_ID_ATTRIBUTE));
        assertNotNull(chain.getRequest());
    }
}
