package org.java.diploma.service.game.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AnalyticsEventPublisherTest {

    @Test
    void publishesSerializedEventToRedisChannel() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsEventPublisher publisher = new AnalyticsEventPublisher(redis, new ObjectMapper());

        publisher.publish("queue_join", 10L, null, Map.of("source", "test"));

        verify(redis).convertAndSend(anyString(), anyString());
    }

    @Test
    void swallowsSerializationErrors() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        doThrow(new JsonProcessingException("bad json") { })
                .when(mapper)
                .writeValueAsString(org.mockito.ArgumentMatchers.anyMap());
        AnalyticsEventPublisher publisher = new AnalyticsEventPublisher(redis, mapper);

        publisher.publish("match_created", 10L, 77L, Map.of());

        verifyNoInteractions(redis);
    }

    @Test
    void swallowsRedisRuntimeErrors() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("redis down")).when(redis).convertAndSend(anyString(), anyString());
        AnalyticsEventPublisher publisher = new AnalyticsEventPublisher(redis, new ObjectMapper());

        publisher.publish("queue_leave", 11L, null, null);

        verify(redis).convertAndSend(anyString(), anyString());
    }
}
