package org.java.diploma.service.game.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Fire-and-forget publisher for gameplay analytics events. Posts JSON to the shared
 * Redis Pub/Sub channel {@code analytics:events} consumed by {@code analytics-service}.
 *
 * <p>Errors are logged and swallowed; analytics must never break gameplay.</p>
 */
@Service
public class AnalyticsEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventPublisher.class);
    private static final String CHANNEL = "analytics:events";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public AnalyticsEventPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void publish(String type, Long userId, Long matchId, Map<String, Object> metadata) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        event.put("userId", userId);
        event.put("matchId", matchId);
        event.put("queueSize", 0);
        event.put("timestamp", Instant.now().toString());
        if (metadata != null && !metadata.isEmpty()) {
            event.put("metadata", metadata);
        }
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize analytics event type={} matchId={}", type, matchId, e);
        } catch (RuntimeException e) {
            log.warn("Failed to publish analytics event type={} matchId={}", type, matchId, e);
        }
    }
}
