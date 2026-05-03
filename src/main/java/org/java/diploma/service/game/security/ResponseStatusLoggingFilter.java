package org.java.diploma.service.game.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Diagnostic: log every non-2xx response leaving game-service so we can correlate browser
 * errors (e.g. 403) with the actual status game-service produced. Runs first in the chain
 * so it observes the final status set by Spring Security or controller advice.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResponseStatusLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ResponseStatusLoggingFilter.class);
    private static final String AUTH_HEADER = "Authorization";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            if (status >= 400) {
                String authHeader = request.getHeader(AUTH_HEADER);
                String tokenPreview = "<absent>";
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    int len = token.length();
                    tokenPreview = len > 20
                            ? token.substring(0, 8) + "…" + token.substring(len - 8) + " len=" + len
                            : "len=" + len;
                }
                log.warn("HTTP {} {} -> {} (origin={}, token={})",
                        request.getMethod(),
                        request.getRequestURI(),
                        status,
                        request.getHeader("Origin"),
                        tokenPreview);
            }
        }
    }
}
