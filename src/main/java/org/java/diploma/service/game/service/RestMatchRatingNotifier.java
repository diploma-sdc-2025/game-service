package org.java.diploma.service.game.service;

import org.java.diploma.service.game.config.AuthInternalClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
public class RestMatchRatingNotifier implements MatchRatingNotifier {

    private static final Logger log = LoggerFactory.getLogger(RestMatchRatingNotifier.class);
    /** Must match auth {@code InternalMatchRatingController}. */
    static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";

    private final RestClient authRestClient;
    private final String internalSecret;

    public RestMatchRatingNotifier(
            @Qualifier(AuthInternalClientConfig.AUTH_INTERNAL_REST_CLIENT) RestClient authRestClient,
            @Value("${diploma.internal-api.secret:}") String internalSecret
    ) {
        this.authRestClient = authRestClient;
        this.internalSecret = internalSecret != null ? internalSecret : "";
    }

    @Override
    public void notifyMatchFinished(long winnerUserId, long loserUserId) {
        if (internalSecret.isBlank()) {
            log.debug("Skipping match rating notify — INTERNAL_API_SECRET not set");
            return;
        }
        try {
            authRestClient.post()
                    .uri("/api/internal/match-rating")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HEADER_INTERNAL_SECRET, internalSecret)
                    .body(Map.of(
                            "winnerUserId", winnerUserId,
                            "loserUserId", loserUserId
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Notified auth of match outcome winner={} loser={}", winnerUserId, loserUserId);
        } catch (RestClientException e) {
            log.warn("Auth match-rating POST failed winner={} loser={}: {}",
                    winnerUserId, loserUserId, e.getMessage());
        }
    }
}
