package org.java.diploma.service.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java.diploma.service.game.dto.BattleEngineEvaluateResponse;
import org.java.diploma.service.game.dto.BattleRoundEvaluationResponse;
import org.java.diploma.service.game.dto.BoardPieceResponse;
import org.java.diploma.service.game.dto.KingSquareResponse;
import org.java.diploma.service.game.entity.MatchPlayer;
import org.java.diploma.service.game.entity.Piece;
import org.java.diploma.service.game.entity.PlayerInventory;
import org.java.diploma.service.game.repository.MatchPlayerRepository;
import org.java.diploma.service.game.repository.PieceRepository;
import org.java.diploma.service.game.repository.PlayerInventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchBattleEvaluationServiceTest {

    private final MatchPlayerRepository matchPlayers = mock(MatchPlayerRepository.class);
    private final PlayerInventoryRepository inventory = mock(PlayerInventoryRepository.class);
    private final PieceRepository pieces = mock(PieceRepository.class);
    private final GameStateRedisService redisState = mock(GameStateRedisService.class);
    private final RestClient battleRestClient = mock(RestClient.class);
    private final GameService gameService = mock(GameService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MatchBattleEvaluationService service = new MatchBattleEvaluationService(
            matchPlayers, inventory, pieces, redisState, battleRestClient, gameService, objectMapper);

    @Test
    void evaluateRound_rejectsPlayerOutsideMatch() {
        when(matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.evaluateRound(1, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not part of this match");
    }

    @Test
    void evaluateRound_requiresExactlyTwoPlayers() {
        when(matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);
        when(matchPlayers.findAllByMatchId(1)).thenReturn(List.of(player(10L)));

        assertThatThrownBy(() -> service.evaluateRound(1, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly two players");
    }

    @Test
    void evaluateRound_usesCachedEvaluationWhenPresent() throws Exception {
        when(matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);
        when(matchPlayers.findAllByMatchId(1)).thenReturn(List.of(player(10L), player(20L)));
        when(inventory.findAllByMatchIdAndUserId(anyInt(), anyLong())).thenReturn(List.of());
        when(redisState.getKingSquare(1, 10L)).thenReturn(new KingSquareResponse(4, 7));
        when(redisState.getKingSquare(1, 20L)).thenReturn(new KingSquareResponse(4, 7));
        when(redisState.getShopRound(1)).thenReturn(3);
        BattleRoundEvaluationResponse cached = response(false);
        when(redisState.getCachedBattleEval(1, 3)).thenReturn(Optional.of(objectMapper.writeValueAsString(cached)));

        BattleRoundEvaluationResponse out = service.evaluateRound(1, 10L);

        assertThat(out.currentUserIsWhite()).isTrue();
        verify(redisState).markBattleViewedByUser(1, 3, 10L);
    }

    @Test
    void evaluateRound_failsForCorruptCachePayload() {
        when(matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);
        when(matchPlayers.findAllByMatchId(1)).thenReturn(List.of(player(10L), player(20L)));
        when(inventory.findAllByMatchIdAndUserId(anyInt(), anyLong())).thenReturn(List.of());
        when(redisState.getKingSquare(1, 10L)).thenReturn(new KingSquareResponse(4, 7));
        when(redisState.getKingSquare(1, 20L)).thenReturn(new KingSquareResponse(4, 7));
        when(redisState.getShopRound(1)).thenReturn(3);
        when(redisState.getCachedBattleEval(1, 3)).thenReturn(Optional.of("{bad-json"));

        assertThatThrownBy(() -> service.evaluateRound(1, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("corrupt");
    }

    @Test
    void evaluateRound_failsWhenKingMissing() {
        when(matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);
        when(matchPlayers.findAllByMatchId(1)).thenReturn(List.of(player(10L), player(20L)));
        when(inventory.findAllByMatchIdAndUserId(anyInt(), anyLong())).thenReturn(List.of());
        when(redisState.getKingSquare(1, 10L)).thenReturn(null);
        when(redisState.getKingSquare(1, 20L)).thenReturn(new KingSquareResponse(4, 7));

        assertThatThrownBy(() -> service.evaluateRound(1, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("King positions not available");
    }

    private MatchPlayer player(long userId) {
        MatchPlayer player = new MatchPlayer();
        player.setUserId(userId);
        return player;
    }

    private BattleRoundEvaluationResponse response(boolean viewerWhite) {
        return new BattleRoundEvaluationResponse(
                "fen",
                100,
                "white",
                10L,
                20L,
                viewerWhite,
                List.of(new BoardPieceResponse(1, 6, "pawn")),
                List.of(new BoardPieceResponse(6, 1, "pawn")),
                new KingSquareResponse(4, 7),
                new KingSquareResponse(4, 7),
                List.of("e2e4"),
                111L,
                100,
                95
        );
    }
}
