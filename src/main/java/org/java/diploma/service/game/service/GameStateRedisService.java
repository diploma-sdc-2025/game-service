package org.java.diploma.service.game.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GameStateRedisService {

    private final RedisTemplate<String, Object> redis;

    public GameStateRedisService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public void initMatchState(long matchId) {
        redis.opsForValue().set(stateKey(matchId), Map.of(
                "phase", "SHOP",
                "round", 1
        ));
        // empty board for now (you can define size later)
        redis.opsForValue().set(boardKey(matchId), new int[0][0]);
    }

    public Object getState(long matchId) {
        return redis.opsForValue().get(stateKey(matchId));
    }

    public Object getBoard(long matchId) {
        return redis.opsForValue().get(boardKey(matchId));
    }

    private String stateKey(long matchId) {
        return "game:state:" + matchId;
    }

    private String boardKey(long matchId) {
        return "game:board:" + matchId;
    }
}
