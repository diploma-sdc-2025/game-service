package org.java.diploma.service.game.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RestMatchRatingNotifierTest {

    @Test
    void skipsCallWhenInternalSecretMissing() {
        RestClient restClient = mock(RestClient.class);
        RestMatchRatingNotifier notifier = new RestMatchRatingNotifier(restClient, "   ");

        notifier.notifyMatchFinished(10L, 20L);

        verify(restClient, never()).post();
    }

    @Test
    void postsMatchResultWhenSecretConfigured() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);

        RestMatchRatingNotifier notifier = new RestMatchRatingNotifier(restClient, "secret");
        assertThatCode(() -> notifier.notifyMatchFinished(99L, 44L)).doesNotThrowAnyException();

        verify(restClient, atLeastOnce()).post();
    }

    @Test
    void swallowsRestClientErrors() {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        when(restClient.post()
                .uri("/api/internal/match-rating")
                .contentType(any())
                .header(RestMatchRatingNotifier.HEADER_INTERNAL_SECRET, "secret")
                .body(any())
                .retrieve()).thenThrow(new RestClientException("boom"));

        RestMatchRatingNotifier notifier = new RestMatchRatingNotifier(restClient, "secret");

        assertThatCode(() -> notifier.notifyMatchFinished(1L, 2L)).doesNotThrowAnyException();

        verify(restClient, atLeastOnce()).post();
    }
}
