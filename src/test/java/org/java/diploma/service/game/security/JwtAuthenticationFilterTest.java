package org.java.diploma.service.game.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    static final String SECRET = "test-test-test-test-test-test-test-test";

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingHeader_doesNotAuthenticate() throws Exception {
        var filter = new JwtAuthenticationFilter(SECRET);
        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validJwt_setsAuthentication() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String jwt = Jwts.builder()
                .subject("user-123")
                .expiration(new Date(System.currentTimeMillis() + 10000))
                .signWith(key)
                .compact();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + jwt);

        var filter = new JwtAuthenticationFilter(SECRET);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull();
    }
}

