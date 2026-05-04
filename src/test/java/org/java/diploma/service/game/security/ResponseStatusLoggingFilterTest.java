package org.java.diploma.service.game.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ResponseStatusLoggingFilterTest {

    @Test
    void keepsRequestFlowForSuccessfulResponse() throws Exception {
        ResponseStatusLoggingFilter filter = new ResponseStatusLoggingFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/game/matches/1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            ((MockHttpServletResponse) invocation.getArgument(1)).setStatus(200);
            return null;
        }).when(chain).doFilter(req, res);

        assertThatCode(() -> filter.doFilter(req, res, chain)).doesNotThrowAnyException();
    }

    @Test
    void keepsRequestFlowForErrorResponseWithBearerToken() throws Exception {
        ResponseStatusLoggingFilter filter = new ResponseStatusLoggingFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/game/pieces/move");
        req.addHeader("Authorization", "Bearer abcdefghijklmnopqrstuvwxyz0123456789");
        req.addHeader("Origin", "https://kon-autochess.francecentral.cloudapp.azure.com");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            ((MockHttpServletResponse) invocation.getArgument(1)).setStatus(403);
            return null;
        }).when(chain).doFilter(req, res);

        assertThatCode(() -> filter.doFilter(req, res, chain)).doesNotThrowAnyException();
    }
}
