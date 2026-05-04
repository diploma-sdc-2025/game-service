package org.java.diploma.service.game.service;

import org.java.diploma.service.game.dto.KingSquareResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameStateRedisServiceTest {

    private RedisTemplate<String, Object> redis;
    private StringRedisTemplate stringRedis;
    private ValueOperations<String, Object> valueOps;
    private ValueOperations<String, String> stringValueOps;
    private HashOperations<String, Object, Object> hashOps;
    private SetOperations<String, String> setOps;
    private GameStateRedisService service;

    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        stringRedis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        stringValueOps = mock(ValueOperations.class);
        hashOps = mock(HashOperations.class);
        setOps = mock(SetOperations.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(stringRedis.opsForValue()).thenReturn(stringValueOps);
        when(stringRedis.opsForSet()).thenReturn(setOps);

        service = new GameStateRedisService(redis, stringRedis);
    }

    @Test
    void initMatchState_seedsStateBoardAndTimer() {
        service.initMatchState(7);

        verify(valueOps).set("game:state:7", Map.of("phase", "SHOP", "round", 1));
        verify(valueOps).set("game:board:7", new int[0][0]);
        verify(stringRedis, times(2)).delete(anyString());
    }

    @Test
    void ensureAndGetShopPhaseEndsAtMillis_usesExistingDeadline() {
        when(valueOps.get("game:state:5")).thenReturn(Map.of("round", 2));
        when(stringValueOps.get("game:shopTimerRound:5")).thenReturn("2");
        when(stringValueOps.get("game:shopPhaseEndsAt:5")).thenReturn("12345");

        long endsAt = service.ensureAndGetShopPhaseEndsAtMillis(5);

        assertThat(endsAt).isEqualTo(12345L);
    }

    @Test
    void ensureAndGetShopPhaseEndsAtMillis_reseedsWhenValueInvalid() {
        when(valueOps.get("game:state:5")).thenReturn(Map.of("round", 2));
        when(stringValueOps.get("game:shopTimerRound:5")).thenReturn("2");
        when(stringValueOps.get("game:shopPhaseEndsAt:5")).thenReturn("oops");

        long endsAt = service.ensureAndGetShopPhaseEndsAtMillis(5);

        assertThat(endsAt).isGreaterThan(0L);
        verify(stringValueOps).set("game:shopPhaseEndsAt:5", Long.toString(endsAt));
    }

    @Test
    void getStateAndBoard_readBackFromRedis() {
        when(valueOps.get("game:state:2")).thenReturn("state");
        when(valueOps.get("game:board:2")).thenReturn("board");

        assertThat(service.getState(2)).isEqualTo("state");
        assertThat(service.getBoard(2)).isEqualTo("board");
    }

    @Test
    void boardSquareOperations_roundTrip() {
        when(hashOps.get("game:playerBoard:1:2", "3:4")).thenReturn("pawn");
        when(hashOps.hasKey("game:playerBoard:1:2", "3:4")).thenReturn(true);

        service.setPlayerBoardSquare(1, 2, 3, 4, "pawn");
        service.clearPlayerBoardSquare(1, 2, 3, 4);

        assertThat(service.getPlayerBoardPieceKey(1, 2, 3, 4)).isEqualTo("pawn");
        assertThat(service.isPlayerBoardSquareOccupied(1, 2, 3, 4)).isTrue();
        verify(hashOps).put("game:playerBoard:1:2", "3:4", "pawn");
        verify(hashOps).delete("game:playerBoard:1:2", "3:4");
    }

    @Test
    void getPlayerBoard_ignoresMalformedEntries() {
        when(hashOps.entries("game:playerBoard:3:9")).thenReturn(Map.of(
                "1:6", "pawn",
                "bad", "rook",
                "2:not", "bishop"
        ));

        var board = service.getPlayerBoard(3, 9);

        assertThat(board).containsExactly(new GameStateRedisService.RuntimeBoardPiece(1, 6, "pawn"));
    }

    @Test
    void replacePlayerBoard_deletesAndReplacesEntries() {
        service.replacePlayerBoard(5, 6, List.of(
                new GameStateRedisService.RuntimeBoardPiece(1, 6, "pawn"),
                new GameStateRedisService.RuntimeBoardPiece(2, 5, "knight")
        ));

        verify(redis).delete("game:playerBoard:5:6");
        verify(hashOps).put("game:playerBoard:5:6", "1:6", "pawn");
        verify(hashOps).put("game:playerBoard:5:6", "2:5", "knight");
    }

    @Test
    void replacePlayerBoard_withEmptyListOnlyDeletes() {
        service.replacePlayerBoard(5, 6, List.of());
        verify(redis).delete("game:playerBoard:5:6");
        verify(hashOps, never()).put(anyString(), any(), any());
    }

    @Test
    void playerBoardAndBenchDataPresence_checksHashSize() {
        when(hashOps.size("game:playerBoard:1:2")).thenReturn(2L);
        when(hashOps.size("game:playerBench:1:2")).thenReturn(0L);

        assertThat(service.hasAnyPlayerBoardData(1, 2)).isTrue();
        assertThat(service.hasAnyBenchData(1, 2)).isFalse();
    }

    @Test
    void playerResourceReadsFallbackOnMalformedValues() {
        when(hashOps.get("game:playerRes:1:2", "gold")).thenReturn("bad");
        when(hashOps.get("game:playerRes:1:2", "hp")).thenReturn(null);

        assertThat(service.getPlayerGold(1, 2, 7)).isEqualTo(7);
        assertThat(service.getPlayerHp(1, 2, 21)).isEqualTo(21);
    }

    @Test
    void playerResourceWritesAndInitIfAbsent() {
        when(redis.hasKey("game:playerRes:4:5")).thenReturn(false, true);

        service.initPlayerResourcesIfAbsent(4, 5, 3, 30);
        service.initPlayerResourcesIfAbsent(4, 5, 9, 99);
        service.setPlayerGold(4, 5, 8);
        service.setPlayerHp(4, 5, 22);

        verify(hashOps).putAll("game:playerRes:4:5", Map.of("gold", "3", "hp", "30"));
        verify(hashOps).put("game:playerRes:4:5", "gold", "8");
        verify(hashOps).put("game:playerRes:4:5", "hp", "22");
    }

    @Test
    void benchOperations_roundTripAndReplace() {
        when(hashOps.get("game:playerBench:2:3", "1")).thenReturn("pawn");
        when(hashOps.hasKey("game:playerBench:2:3", "1")).thenReturn(true);
        when(hashOps.entries("game:playerBench:2:3")).thenReturn(Map.of("1", "pawn", "bad", "x"));

        service.setBenchSlot(2, 3, 1, "pawn");
        service.clearBenchSlot(2, 3, 1);
        assertThat(service.getBenchSlotPieceKey(2, 3, 1)).isEqualTo("pawn");
        assertThat(service.isBenchSlotOccupied(2, 3, 1)).isTrue();
        assertThat(service.getBench(2, 3))
                .containsExactly(new GameStateRedisService.RuntimeBenchPiece(1, "pawn"));

        service.replaceBench(2, 3, List.of(new GameStateRedisService.RuntimeBenchPiece(0, "knight")));
        verify(redis).delete("game:playerBench:2:3");
        verify(hashOps).put("game:playerBench:2:3", "0", "knight");
    }

    @Test
    void initPlayerKing_skipsWhenAlreadyExists() {
        when(redis.hasKey("game:king:1:2")).thenReturn(true);

        service.initPlayerKing(1, 2);

        verify(redis).hasKey("game:king:1:2");
    }

    @Test
    void initPlayerKing_setsDefaultWhenMissing() {
        when(redis.hasKey("game:king:1:2")).thenReturn(false);

        service.initPlayerKing(1, 2);

        verify(hashOps).putAll("game:king:1:2", Map.of("x", "4", "y", "7"));
    }

    @Test
    void kingSquare_roundTripAndOptional() {
        when(hashOps.get("game:king:1:2", "x")).thenReturn("3");
        when(hashOps.get("game:king:1:2", "y")).thenReturn("6");

        KingSquareResponse king = service.getKingSquare(1, 2);
        Optional<KingSquareResponse> maybeKing = service.getKingSquareOrEmpty(1, 2);

        assertThat(king).isEqualTo(new KingSquareResponse(3, 6));
        assertThat(maybeKing).contains(new KingSquareResponse(3, 6));
    }

    @Test
    void getKingSquare_returnsNullWhenMissingValues() {
        when(hashOps.get("game:king:1:2", "x")).thenReturn(null);

        assertThat(service.getKingSquare(1, 2)).isNull();
    }

    @Test
    void setKingSquare_writesHashValues() {
        service.setKingSquare(4, 9, 5, 7);
        verify(hashOps).putAll("game:king:4:9", Map.of("x", "5", "y", "7"));
    }

    @Test
    void getShopRound_handlesMapNumberStringAndFallback() {
        when(valueOps.get("game:state:1")).thenReturn(Map.of("round", 4));
        assertThat(service.getShopRound(1)).isEqualTo(4);

        when(valueOps.get("game:state:1")).thenReturn(Map.of("round", "6"));
        assertThat(service.getShopRound(1)).isEqualTo(6);

        when(valueOps.get("game:state:1")).thenReturn(Map.of("round", "nope"));
        assertThat(service.getShopRound(1)).isEqualTo(1);

        when(valueOps.get("game:state:1")).thenReturn("not-a-map");
        assertThat(service.getShopRound(1)).isEqualTo(1);
    }

    @Test
    void clearShopPhaseTimer_deletesKeys() {
        service.clearShopPhaseTimer(3);
        verify(stringRedis).delete("game:shopPhaseEndsAt:3");
        verify(stringRedis).delete("game:shopTimerRound:3");
    }

    @Test
    void incrementShopRoundAfterBattle_preservesPhaseAndIncrementsRound() {
        when(valueOps.get("game:state:4")).thenReturn(Map.of("phase", "SHOP", "round", 2));

        service.incrementShopRoundAfterBattle(4, 9_999_888_777L);

        verify(valueOps).set("game:state:4", Map.of("phase", "SHOP", "round", 3));
        verify(stringValueOps).set("game:shopPhaseEndsAt:4", "9999888777");
        verify(stringValueOps).set("game:shopTimerRound:4", "3");
    }

    @Test
    void cachedBattleEval_andLastBattleEval_roundTrip() {
        service.setCachedBattleEval(9, 2, "{\"x\":1}");
        verify(stringValueOps).set("game:battleEval:v2:9:2", "{\"x\":1}", Duration.ofHours(48));

        when(stringValueOps.get("game:battleEval:v2:9:2")).thenReturn("{\"x\":1}");
        assertThat(service.getCachedBattleEval(9, 2)).contains("{\"x\":1}");

        service.setLastBattleEval(9, 2, "{\"y\":2}");
        verify(stringValueOps).set("game:lastBattleEval:v1:9", "{\"y\":2}", Duration.ofHours(48));
        verify(stringValueOps).set("game:lastBattleRound:v1:9", "2", Duration.ofHours(48));

        when(stringValueOps.get("game:lastBattleRound:v1:9")).thenReturn("2");
        when(stringValueOps.get("game:lastBattleEval:v1:9")).thenReturn("{\"y\":2}");
        assertThat(service.getLastBattleEval(9))
                .contains(new GameStateRedisService.LastBattleEval(2, "{\"y\":2}"));
    }

    @Test
    void getLastBattleEval_returnsEmptyForInvalidRound() {
        when(stringValueOps.get("game:lastBattleRound:v1:9")).thenReturn("oops");
        when(stringValueOps.get("game:lastBattleEval:v1:9")).thenReturn("{\"y\":2}");
        assertThat(service.getLastBattleEval(9)).isEmpty();
    }

    @Test
    void getLastBattleEval_returnsEmptyWhenEitherFieldMissing() {
        when(stringValueOps.get("game:lastBattleRound:v1:9")).thenReturn(null);
        when(stringValueOps.get("game:lastBattleEval:v1:9")).thenReturn("{\"y\":2}");
        assertThat(service.getLastBattleEval(9)).isEmpty();
    }

    @Test
    void markAndCheckBattleViewedState() {
        when(setOps.isMember("game:battleViewed:v1:8:3", "11")).thenReturn(true);

        assertThat(service.hasBattleBeenViewedByUser(8, 3, 11)).isTrue();

        service.markBattleViewedByUser(8, 3, 11);
        verify(setOps).add("game:battleViewed:v1:8:3", "11");
        verify(stringRedis).expire("game:battleViewed:v1:8:3", Duration.ofHours(48));
    }

    @Test
    void tryMarkBattleRoundApplied_returnsBooleanFromSetIfAbsent() {
        when(stringValueOps.setIfAbsent("game:battleApplied:v1:8:4", "1", Duration.ofHours(48))).thenReturn(true, false, null);

        assertThat(service.tryMarkBattleRoundApplied(8, 4)).isTrue();
        assertThat(service.tryMarkBattleRoundApplied(8, 4)).isFalse();
        assertThat(service.tryMarkBattleRoundApplied(8, 4)).isFalse();
    }

    @Test
    void runtimeTouchedFlags_useRedisKeys() {
        when(redis.hasKey("game:runtimeBoardTouched:v1:1:2")).thenReturn(true);
        when(redis.hasKey("game:runtimeBenchTouched:v1:1:2")).thenReturn(false);

        assertThat(service.isRuntimeShopBoardTouched(1, 2)).isTrue();
        assertThat(service.isRuntimeShopBenchTouched(1, 2)).isFalse();

        service.markRuntimeShopBoardTouched(1, 2);
        service.markRuntimeShopBenchTouched(1, 2);
        verify(valueOps).set("game:runtimeBoardTouched:v1:1:2", "1");
        verify(valueOps).set("game:runtimeBenchTouched:v1:1:2", "1");
    }
}
