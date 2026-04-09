package org.java.diploma.service.game.service;

import org.java.diploma.service.game.dto.KingSquareResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class GameStateRedisService {

    private static final Logger logger = LoggerFactory.getLogger(GameStateRedisService.class);

    private static final String REDIS_KEY_PREFIX_STATE = "game:state:";
    private static final String REDIS_KEY_PREFIX_BOARD = "game:board:";
    private static final String REDIS_KEY_PREFIX_KING = "game:king:";
    /** v2: per-cycle round is advanced after each battle so keys rotate with new shop setups. */
    private static final String REDIS_KEY_PREFIX_BATTLE_EVAL = "game:battleEval:v2:";
    private static final String REDIS_KEY_SHOP_PHASE_ENDS = "game:shopPhaseEndsAt:";
    private static final String REDIS_KEY_SHOP_TIMER_ROUND = "game:shopTimerRound:";

    /** Wall-clock shop placement window length (shared by all clients via Redis). */
    public static final long SHOP_PHASE_DURATION_SECONDS = 30L;

    private static final String STATE_KEY_PHASE = "phase";
    private static final String STATE_KEY_ROUND = "round";

    private static final String PHASE_SHOP = "SHOP";
    private static final int INITIAL_ROUND = 1;

    private static final String LOG_INIT_MATCH_STATE = "Initializing match state in Redis: matchId={}";
    private static final String LOG_STATE_INITIALIZED = "Match state initialized: matchId={}, phase={}, round={}";
    private static final String LOG_GET_STATE = "Retrieving game state from Redis: matchId={}";
    private static final String LOG_GET_BOARD = "Retrieving game board from Redis: matchId={}";
    private static final String HASH_KEY_X = "x";
    private static final String HASH_KEY_Y = "y";

    private static final int DEFAULT_KING_COL = 4;
    private static final int DEFAULT_KING_ROW = 7;

    private static final Duration BATTLE_EVAL_CACHE_TTL = Duration.ofHours(48);

    private final RedisTemplate<String, Object> redis;
    private final StringRedisTemplate stringRedis;

    public GameStateRedisService(RedisTemplate<String, Object> redis, StringRedisTemplate stringRedis) {
        this.redis = redis;
        this.stringRedis = stringRedis;
    }

    public void initMatchState(long matchId) {
        logger.info(LOG_INIT_MATCH_STATE, matchId);

        redis.opsForValue().set(stateKey(matchId), Map.of(
                STATE_KEY_PHASE, PHASE_SHOP,
                STATE_KEY_ROUND, INITIAL_ROUND
        ));

        redis.opsForValue().set(boardKey(matchId), new int[0][0]);

        seedShopPhaseTimerForRound(matchId, INITIAL_ROUND);

        logger.info(LOG_STATE_INITIALIZED, matchId, PHASE_SHOP, INITIAL_ROUND);
    }

    /**
     * Ensures a deadline exists for the current Redis shop round and returns epoch millis when the shop closes.
     * All players polling {@code /shop} share the same instant so countdowns stay aligned across tabs.
     */
    public long ensureAndGetShopPhaseEndsAtMillis(long matchId) {
        int round = getShopRound(matchId);
        String rStr = stringRedis.opsForValue().get(shopTimerRoundKey(matchId));
        String eStr = stringRedis.opsForValue().get(shopEndsKey(matchId));
        if (rStr == null || eStr == null || !rStr.equals(Integer.toString(round))) {
            return seedShopPhaseTimerForRound(matchId, round);
        }
        try {
            return Long.parseLong(eStr);
        } catch (NumberFormatException e) {
            return seedShopPhaseTimerForRound(matchId, round);
        }
    }

    private long seedShopPhaseTimerForRound(long matchId, int round) {
        long ends = Instant.now().plusSeconds(SHOP_PHASE_DURATION_SECONDS).toEpochMilli();
        stringRedis.opsForValue().set(shopEndsKey(matchId), Long.toString(ends));
        stringRedis.opsForValue().set(shopTimerRoundKey(matchId), Integer.toString(round));
        logger.debug("Shop phase deadline set: matchId={}, round={}, endsAtEpochMs={}", matchId, round, ends);
        return ends;
    }

    private String shopEndsKey(long matchId) {
        return REDIS_KEY_SHOP_PHASE_ENDS + matchId;
    }

    private String shopTimerRoundKey(long matchId) {
        return REDIS_KEY_SHOP_TIMER_ROUND + matchId;
    }

    public Object getState(long matchId) {
        logger.debug(LOG_GET_STATE, matchId);
        return redis.opsForValue().get(stateKey(matchId));
    }

    public Object getBoard(long matchId) {
        logger.debug(LOG_GET_BOARD, matchId);
        return redis.opsForValue().get(boardKey(matchId));
    }

    /** e1 default at white's back rank. */
    public void initPlayerKing(long matchId, long userId) {
        String key = kingKey(matchId, userId);
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return;
        }
        redis.opsForHash().putAll(key, Map.of(
                HASH_KEY_X, String.valueOf(DEFAULT_KING_COL),
                HASH_KEY_Y, String.valueOf(DEFAULT_KING_ROW)));
    }

    public KingSquareResponse getKingSquare(long matchId, long userId) {
        String key = kingKey(matchId, userId);
        Object xo = redis.opsForHash().get(key, HASH_KEY_X);
        Object yo = redis.opsForHash().get(key, HASH_KEY_Y);
        if (xo == null || yo == null) {
            return null;
        }
        int x = Integer.parseInt(xo.toString());
        int y = Integer.parseInt(yo.toString());
        return new KingSquareResponse(x, y);
    }

    public Optional<KingSquareResponse> getKingSquareOrEmpty(long matchId, long userId) {
        return Optional.ofNullable(getKingSquare(matchId, userId));
    }

    public void setKingSquare(long matchId, long userId, int x, int y) {
        String key = kingKey(matchId, userId);
        redis.opsForHash().putAll(key, Map.of(
                HASH_KEY_X, String.valueOf(x),
                HASH_KEY_Y, String.valueOf(y)));
    }

    /**
     * Shop / battle cycle round from Redis match state (defaults to 1). Used to key idempotent battle-eval cache.
     */
    public int getShopRound(long matchId) {
        return readRoundFromState(redis.opsForValue().get(stateKey(matchId)));
    }

    /**
     * Clears the shared shop countdown so the next placement window does not start until clients call
     * {@link #ensureAndGetShopPhaseEndsAtMillis(long)} (via {@code GET /shop}). Otherwise the deadline was
     * seeded at battle-finalize time while players were still in the battle UI, leaving only a few seconds
     * of shop time after replay.
     */
    public void clearShopPhaseTimer(long matchId) {
        stringRedis.delete(shopEndsKey(matchId));
        stringRedis.delete(shopTimerRoundKey(matchId));
        logger.debug("Cleared shop phase timer keys: matchId={}", matchId);
    }

    /**
     * After a battle round is fully resolved and cached, advance the cycle counter so the next shop → battle
     * uses a fresh cache key and rebuilds FEN from current DB pieces.
     */
    public void incrementShopRoundAfterBattle(long matchId) {
        Object raw = redis.opsForValue().get(stateKey(matchId));
        String phase = readPhaseFromState(raw);
        int current = readRoundFromState(raw);
        int next = current + 1;
        redis.opsForValue().set(stateKey(matchId), Map.of(STATE_KEY_PHASE, phase, STATE_KEY_ROUND, next));
        clearShopPhaseTimer(matchId);
        logger.info("Advanced shop/battle cycle: matchId={}, round {} -> {}", matchId, current, next);
    }

    private int readRoundFromState(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return INITIAL_ROUND;
        }
        Object r = map.get(STATE_KEY_ROUND);
        if (r instanceof Number n) {
            return Math.max(INITIAL_ROUND, n.intValue());
        }
        if (r instanceof String s) {
            try {
                return Math.max(INITIAL_ROUND, Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                return INITIAL_ROUND;
            }
        }
        return INITIAL_ROUND;
    }

    private String readPhaseFromState(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return PHASE_SHOP;
        }
        Object p = map.get(STATE_KEY_PHASE);
        return p != null ? p.toString() : PHASE_SHOP;
    }

    public Optional<String> getCachedBattleEval(long matchId, int round) {
        String key = battleEvalKey(matchId, round);
        return Optional.ofNullable(stringRedis.opsForValue().get(key));
    }

    public void setCachedBattleEval(long matchId, int round, String json) {
        stringRedis.opsForValue().set(battleEvalKey(matchId, round), json, BATTLE_EVAL_CACHE_TTL);
    }

    private String battleEvalKey(long matchId, int round) {
        return REDIS_KEY_PREFIX_BATTLE_EVAL + matchId + ":" + round;
    }

    private String stateKey(long matchId) {
        return REDIS_KEY_PREFIX_STATE + matchId;
    }

    private String boardKey(long matchId) {
        return REDIS_KEY_PREFIX_BOARD + matchId;
    }

    private String kingKey(long matchId, long userId) {
        return REDIS_KEY_PREFIX_KING + matchId + ":" + userId;
    }
}