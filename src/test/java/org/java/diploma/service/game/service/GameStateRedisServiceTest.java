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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
    void markAndCheckBattleViewedState() {
        when(setOps.isMember("game:battleViewed:v1:8:3", "11")).thenReturn(true);

        assertThat(service.hasBattleBeenViewedByUser(8, 3, 11)).isTrue();

        service.markBattleViewedByUser(8, 3, 11);
        verify(setOps).add("game:battleViewed:v1:8:3", "11");
        verify(stringRedis).expire("game:battleViewed:v1:8:3", Duration.ofHours(48));
    }
}
