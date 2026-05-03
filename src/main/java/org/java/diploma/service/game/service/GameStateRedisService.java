package org.java.diploma.service.game.service;

import org.java.diploma.service.game.dto.KingSquareResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GameStateRedisService {

    private static final Logger logger = LoggerFactory.getLogger(GameStateRedisService.class);

    private static final String REDIS_KEY_PREFIX_STATE = "game:state:";
    private static final String REDIS_KEY_PREFIX_BOARD = "game:board:";
    private static final String REDIS_KEY_PREFIX_PLAYER_BOARD = "game:playerBoard:";
    private static final String REDIS_KEY_PREFIX_PLAYER_BENCH = "game:playerBench:";
    /** When set, an empty Redis board hash means "sold everything", not "never seeded" (avoid stale DB replay). */
    private static final String REDIS_KEY_PREFIX_RUNTIME_BOARD_TOUCHED = "game:runtimeBoardTouched:v1:";
    /** Same for bench: selling the last bench piece must not re-trigger {@code loadBenchFromDb}. */
    private static final String REDIS_KEY_PREFIX_RUNTIME_BENCH_TOUCHED = "game:runtimeBenchTouched:v1:";
    private static final String REDIS_KEY_PREFIX_PLAYER_RES = "game:playerRes:";
    private static final String REDIS_KEY_PREFIX_KING = "game:king:";
    /** v2: per-cycle round is advanced after each battle so keys rotate with new shop setups. */
    private static final String REDIS_KEY_PREFIX_BATTLE_EVAL = "game:battleEval:v2:";
    /**
     * Single-slot "last battle" snapshot used as a safety net when one client is late and the round already
     * advanced. Prevents a player from silently missing battle and continuing shopping.
     */
    private static final String REDIS_KEY_LAST_BATTLE_EVAL = "game:lastBattleEval:v1:";
    private static final String REDIS_KEY_LAST_BATTLE_ROUND = "game:lastBattleRound:v1:";
    private static final String REDIS_KEY_BATTLE_VIEWED_PREFIX = "game:battleViewed:v1:";
    private static final String REDIS_KEY_BATTLE_APPLIED_PREFIX = "game:battleApplied:v1:";
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
    private static final String HASH_KEY_GOLD = "gold";
    private static final String HASH_KEY_HP = "hp";

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
        // Do not start the shop timer at matchmaking time.
        // First /shop request should start it so players get full round duration.
        clearShopPhaseTimer(matchId);

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

    /**
     * Runtime board occupancy (per player) for fast move validation.
     * Hash field: {@code "x:y"} -> piece key (e.g. pawn, knight).
     */
    public void setPlayerBoardSquare(long matchId, long userId, int x, int y, String pieceKey) {
        redis.opsForHash().put(playerBoardKey(matchId, userId), boardSquareField(x, y), pieceKey);
    }

    public void clearPlayerBoardSquare(long matchId, long userId, int x, int y) {
        redis.opsForHash().delete(playerBoardKey(matchId, userId), boardSquareField(x, y));
    }

    public boolean isPlayerBoardSquareOccupied(long matchId, long userId, int x, int y) {
        return redis.opsForHash().hasKey(playerBoardKey(matchId, userId), boardSquareField(x, y));
    }

    public String getPlayerBoardPieceKey(long matchId, long userId, int x, int y) {
        Object raw = redis.opsForHash().get(playerBoardKey(matchId, userId), boardSquareField(x, y));
        return raw == null ? null : raw.toString();
    }

    public List<RuntimeBoardPiece> getPlayerBoard(long matchId, long userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(playerBoardKey(matchId, userId));
        List<RuntimeBoardPiece> pieces = new ArrayList<>();
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            String field = e.getKey() == null ? null : e.getKey().toString();
            if (field == null || !field.contains(":")) {
                continue;
            }
            String[] parts = field.split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                String pieceKey = e.getValue() == null ? "" : e.getValue().toString();
                pieces.add(new RuntimeBoardPiece(x, y, pieceKey));
            } catch (NumberFormatException ignored) {
                // ignore malformed fields
            }
        }
        return pieces;
    }

    public boolean hasAnyPlayerBoardData(long matchId, long userId) {
        Long size = redis.opsForHash().size(playerBoardKey(matchId, userId));
        return size != null && size > 0;
    }

    public void replacePlayerBoard(long matchId, long userId, List<RuntimeBoardPiece> pieces) {
        String key = playerBoardKey(matchId, userId);
        redis.delete(key);
        if (pieces == null || pieces.isEmpty()) {
            return;
        }
        for (RuntimeBoardPiece piece : pieces) {
            setPlayerBoardSquare(matchId, userId, piece.x(), piece.y(), piece.pieceKey());
        }
    }

    public void initPlayerResourcesIfAbsent(long matchId, long userId, int gold, int hp) {
        String key = playerResourcesKey(matchId, userId);
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return;
        }
        redis.opsForHash().putAll(key, Map.of(
                HASH_KEY_GOLD, Integer.toString(gold),
                HASH_KEY_HP, Integer.toString(hp)
        ));
    }

    public int getPlayerGold(long matchId, long userId, int fallback) {
        Object raw = redis.opsForHash().get(playerResourcesKey(matchId, userId), HASH_KEY_GOLD);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void setPlayerGold(long matchId, long userId, int gold) {
        redis.opsForHash().put(playerResourcesKey(matchId, userId), HASH_KEY_GOLD, Integer.toString(gold));
    }

    public int getPlayerHp(long matchId, long userId, int fallback) {
        Object raw = redis.opsForHash().get(playerResourcesKey(matchId, userId), HASH_KEY_HP);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void setPlayerHp(long matchId, long userId, int hp) {
        redis.opsForHash().put(playerResourcesKey(matchId, userId), HASH_KEY_HP, Integer.toString(hp));
    }

    public void setBenchSlot(long matchId, long userId, int slot, String pieceKey) {
        redis.opsForHash().put(playerBenchKey(matchId, userId), Integer.toString(slot), pieceKey);
    }

    public void clearBenchSlot(long matchId, long userId, int slot) {
        redis.opsForHash().delete(playerBenchKey(matchId, userId), Integer.toString(slot));
    }

    public String getBenchSlotPieceKey(long matchId, long userId, int slot) {
        Object raw = redis.opsForHash().get(playerBenchKey(matchId, userId), Integer.toString(slot));
        return raw == null ? null : raw.toString();
    }

    public boolean isBenchSlotOccupied(long matchId, long userId, int slot) {
        return redis.opsForHash().hasKey(playerBenchKey(matchId, userId), Integer.toString(slot));
    }

    public boolean hasAnyBenchData(long matchId, long userId) {
        Long size = redis.opsForHash().size(playerBenchKey(matchId, userId));
        return size != null && size > 0;
    }

    public List<RuntimeBenchPiece> getBench(long matchId, long userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(playerBenchKey(matchId, userId));
        List<RuntimeBenchPiece> out = new ArrayList<>();
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            try {
                int slot = Integer.parseInt(String.valueOf(e.getKey()));
                String pieceKey = String.valueOf(e.getValue());
                out.add(new RuntimeBenchPiece(slot, pieceKey));
            } catch (Exception ignored) {
                // ignore malformed entries
            }
        }
        return out;
    }

    public void replaceBench(long matchId, long userId, List<RuntimeBenchPiece> pieces) {
        String key = playerBenchKey(matchId, userId);
        redis.delete(key);
        if (pieces == null || pieces.isEmpty()) return;
        for (RuntimeBenchPiece p : pieces) {
            setBenchSlot(matchId, userId, p.slot(), p.pieceKey());
        }
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
     * After a battle round is fully resolved and cached: advance Redis shop-round and pin the shared shop deadline
     * to {@code scheduledShopEndsAtEpochMs} (typically replay end + {@link #SHOP_PHASE_DURATION_SECONDS}s) so players
     * who poll /shop early do not shorten the placement window for others still watching the replay.
     */
    public void incrementShopRoundAfterBattle(long matchId, long scheduledShopEndsAtEpochMs) {
        Object raw = redis.opsForValue().get(stateKey(matchId));
        String phase = readPhaseFromState(raw);
        int current = readRoundFromState(raw);
        int next = current + 1;
        redis.opsForValue().set(stateKey(matchId), Map.of(STATE_KEY_PHASE, phase, STATE_KEY_ROUND, next));
        stringRedis.opsForValue().set(shopEndsKey(matchId), Long.toString(scheduledShopEndsAtEpochMs));
        stringRedis.opsForValue().set(shopTimerRoundKey(matchId), Integer.toString(next));
        logger.info("Advanced shop/battle cycle: matchId={}, round {} -> {}, shopEndsAtEpochMs={}",
                matchId, current, next, scheduledShopEndsAtEpochMs);
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

    public void setLastBattleEval(long matchId, int round, String json) {
        stringRedis.opsForValue().set(lastBattleEvalKey(matchId), json, BATTLE_EVAL_CACHE_TTL);
        stringRedis.opsForValue().set(lastBattleRoundKey(matchId), Integer.toString(round), BATTLE_EVAL_CACHE_TTL);
    }

    public Optional<LastBattleEval> getLastBattleEval(long matchId) {
        String roundStr = stringRedis.opsForValue().get(lastBattleRoundKey(matchId));
        String json = stringRedis.opsForValue().get(lastBattleEvalKey(matchId));
        if (roundStr == null || json == null) {
            return Optional.empty();
        }
        try {
            int round = Integer.parseInt(roundStr);
            return Optional.of(new LastBattleEval(round, json));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public boolean hasBattleBeenViewedByUser(long matchId, int round, long userId) {
        return Boolean.TRUE.equals(stringRedis.opsForSet().isMember(battleViewedKey(matchId, round), Long.toString(userId)));
    }

    public void markBattleViewedByUser(long matchId, int round, long userId) {
        String key = battleViewedKey(matchId, round);
        stringRedis.opsForSet().add(key, Long.toString(userId));
        stringRedis.expire(key, BATTLE_EVAL_CACHE_TTL);
    }

    /**
     * Idempotency guard for battle round side effects (HP/gold/round advance).
     * Returns true only for the first caller for (matchId, round).
     */
    public boolean tryMarkBattleRoundApplied(long matchId, int round) {
        String key = battleAppliedKey(matchId, round);
        Boolean acquired = stringRedis.opsForValue().setIfAbsent(key, "1", BATTLE_EVAL_CACHE_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    private String battleEvalKey(long matchId, int round) {
        return REDIS_KEY_PREFIX_BATTLE_EVAL + matchId + ":" + round;
    }

    private String lastBattleEvalKey(long matchId) {
        return REDIS_KEY_LAST_BATTLE_EVAL + matchId;
    }

    private String lastBattleRoundKey(long matchId) {
        return REDIS_KEY_LAST_BATTLE_ROUND + matchId;
    }

    private String battleViewedKey(long matchId, int round) {
        return REDIS_KEY_BATTLE_VIEWED_PREFIX + matchId + ":" + round;
    }

    private String battleAppliedKey(long matchId, int round) {
        return REDIS_KEY_BATTLE_APPLIED_PREFIX + matchId + ":" + round;
    }

    private String stateKey(long matchId) {
        return REDIS_KEY_PREFIX_STATE + matchId;
    }

    private String boardKey(long matchId) {
        return REDIS_KEY_PREFIX_BOARD + matchId;
    }

    private String playerBoardKey(long matchId, long userId) {
        return REDIS_KEY_PREFIX_PLAYER_BOARD + matchId + ":" + userId;
    }

    private String playerBenchKey(long matchId, long userId) {
        return REDIS_KEY_PREFIX_PLAYER_BENCH + matchId + ":" + userId;
    }

    private String runtimeBoardTouchedKey(long matchId, long userId) {
        return REDIS_KEY_PREFIX_RUNTIME_BOARD_TOUCHED + matchId + ":" + userId;
    }

    private String runtimeBenchTouchedKey(long matchId, long userId) {
        return REDIS_KEY_PREFIX_RUNTIME_BENCH_TOUCHED + matchId + ":" + userId;
    }

    public boolean isRuntimeShopBoardTouched(long matchId, long userId) {
        return Boolean.TRUE.equals(redis.hasKey(runtimeBoardTouchedKey(matchId, userId)));
    }

    /** Marks Redis board once hydrated from DB or after reading any non-null runtime board state. */
    public void markRuntimeShopBoardTouched(long matchId, long userId) {
        redis.opsForValue().set(runtimeBoardTouchedKey(matchId, userId), "1");
    }

    public boolean isRuntimeShopBenchTouched(long matchId, long userId) {
        return Boolean.TRUE.equals(redis.hasKey(runtimeBenchTouchedKey(matchId, userId)));
    }

    public void markRuntimeShopBenchTouched(long matchId, long userId) {
        redis.opsForValue().set(runtimeBenchTouchedKey(matchId, userId), "1");
    }

    private String playerResourcesKey(long matchId, long userId) {
        return REDIS_KEY_PREFIX_PLAYER_RES + matchId + ":" + userId;
    }

    private String boardSquareField(int x, int y) {
        return x + ":" + y;
    }

    private String kingKey(long matchId, long userId) {
        return REDIS_KEY_PREFIX_KING + matchId + ":" + userId;
    }

    public record LastBattleEval(int round, String json) {}
    public record RuntimeBoardPiece(int x, int y, String pieceKey) {}
    public record RuntimeBenchPiece(int slot, String pieceKey) {}
}