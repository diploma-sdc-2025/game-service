package org.java.diploma.service.game.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Diagnostic-first global handler. Logs every uncaught exception so we can correlate
 * front-end status codes (e.g. 403) with the real cause. Without this, Spring Security's
 * AccessDeniedException and Spring's DefaultHandlerExceptionResolver translate exceptions
 * silently with no INFO-level trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> onAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("AccessDeniedException at {} {} reason={}",
                req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        body.setTitle("Forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> onAuthFailure(AuthenticationException ex, HttpServletRequest req) {
        log.warn("AuthenticationException at {} {} reason={}",
                req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        body.setTitle("Unauthorized");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> onResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        if (ex.getStatusCode().is5xxServerError()) {
            log.error("ResponseStatusException {} at {} {} reason={}",
                    ex.getStatusCode(), req.getMethod(), req.getRequestURI(), ex.getReason(), ex);
        } else if (ex.getStatusCode().is4xxClientError()) {
            log.warn("ResponseStatusException {} at {} {} reason={}",
                    ex.getStatusCode(), req.getMethod(), req.getRequestURI(), ex.getReason());
        }
        String detail = ex.getReason() != null ? ex.getReason() : "Request failed";
        ProblemDetail body = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), detail);
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ProblemDetail> onUnhandled(Throwable ex, HttpServletRequest req) {
        log.error("Unhandled exception at {} {}", req.getMethod(), req.getRequestURI(), ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error: " + ex.getClass().getSimpleName());
        body.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
