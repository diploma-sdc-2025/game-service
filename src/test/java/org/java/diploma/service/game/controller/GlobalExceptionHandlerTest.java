package org.java.diploma.service.game.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsAccessDeniedToForbiddenProblemDetail() {
        HttpServletRequest req = request("GET", "/api/game/matches/1");

        var response = handler.onAccessDenied(new AccessDeniedException("forbidden"), req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Forbidden");
        assertThat(response.getBody().getDetail()).isEqualTo("forbidden");
    }

    @Test
    void mapsAuthenticationExceptionToUnauthorizedProblemDetail() {
        HttpServletRequest req = request("POST", "/api/game/pieces/buy");

        var response = handler.onAuthFailure(new BadCredentialsException("bad creds"), req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Unauthorized");
        assertThat(response.getBody().getDetail()).isEqualTo("bad creds");
    }

    @Test
    void mapsResponseStatusExceptionAndFallsBackWhenReasonMissing() {
        HttpServletRequest req = request("POST", "/api/game/unknown");

        var withReason = handler.onResponseStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad request"), req);
        var withoutReason = handler.onResponseStatus(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR), req);

        assertThat(withReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(withReason.getBody()).isNotNull();
        assertThat(withReason.getBody().getDetail()).isEqualTo("bad request");
        assertThat(withoutReason.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(withoutReason.getBody()).isNotNull();
        assertThat(withoutReason.getBody().getDetail()).isEqualTo("Request failed");
    }

    @Test
    void mapsUnhandledThrowableToInternalServerError() {
        HttpServletRequest req = request("GET", "/api/game/matches/1");

        var response = handler.onUnhandled(new IllegalStateException("boom"), req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getDetail()).contains("IllegalStateException");
    }

    private static HttpServletRequest request(String method, String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        return req;
    }
}
