package org.java.diploma.service.game.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GameStateRedisService {

    private static final Logger logger = LoggerFactory.getLogger(GameStateRedisService.class);

    private static final String REDIS_KEY_PREFIX_STATE = "game:state:";
    private static final String REDIS_KEY_PREFIX_BOARD = "game:board:";

    private static final String STATE_KEY_PHASE = "phase";
    private static final String STATE_KEY_ROUND = "round";

    private static final String PHASE_SHOP = "SHOP";
    private static final int INITIAL_ROUND = 1;

    private static final String LOG_INIT_MATCH_STATE = "Initializing match state in Redis: matchId={}";
    private static final String LOG_STATE_INITIALIZED = "Match state initialized: matchId={}, phase={}, round={}";
    private static final String LOG_GET_STATE = "Retrieving game state from Redis: matchId={}";
    private static final String LOG_GET_BOARD = "Retrieving game board from Redis: matchId={}";

    private final RedisTemplate<String, Object> redis;

    public GameStateRedisService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public void initMatchState(long matchId) {
        logger.info(LOG_INIT_MATCH_STATE, matchId);

        redis.opsForValue().set(stateKey(matchId), Map.of(
                STATE_KEY_PHASE, PHASE_SHOP,
                STATE_KEY_ROUND, INITIAL_ROUND
        ));

        redis.opsForValue().set(boardKey(matchId), new int[0][0]);

        logger.info(LOG_STATE_INITIALIZED, matchId, PHASE_SHOP, INITIAL_ROUND);
    }

    public Object getState(long matchId) {
        logger.debug(LOG_GET_STATE, matchId);
        return redis.opsForValue().get(stateKey(matchId));
    }

    public Object getBoard(long matchId) {
        logger.debug(LOG_GET_BOARD, matchId);
        return redis.opsForValue().get(boardKey(matchId));
    }

    private String stateKey(long matchId) {
        return REDIS_KEY_PREFIX_STATE + matchId;
    }

    private String boardKey(long matchId) {
        return REDIS_KEY_PREFIX_BOARD + matchId;
    }
}