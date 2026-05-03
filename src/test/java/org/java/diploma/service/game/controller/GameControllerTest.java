package org.java.diploma.service.game.controller;

import org.java.diploma.service.game.dto.BattleRoundEvaluationResponse;
import org.java.diploma.service.game.dto.BuyPieceRequest;
import org.java.diploma.service.game.dto.BuyPieceResponse;
import org.java.diploma.service.game.dto.CreateMatchRequest;
import org.java.diploma.service.game.dto.KingSquareResponse;
import org.java.diploma.service.game.dto.MatchResponse;
import org.java.diploma.service.game.dto.MoveKingRequest;
import org.java.diploma.service.game.dto.MovePieceRequest;
import org.java.diploma.service.game.dto.PlacePieceRequest;
import org.java.diploma.service.game.dto.SellPieceRequest;
import org.java.diploma.service.game.dto.SellPieceResponse;
import org.java.diploma.service.game.dto.ShopStateResponse;
import org.java.diploma.service.game.service.GameService;
import org.java.diploma.service.game.service.GameStateRedisService;
import org.java.diploma.service.game.service.MatchBattleEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameControllerTest {

    private final GameService game = mock(GameService.class);
    private final GameStateRedisService redisState = mock(GameStateRedisService.class);
    private final MatchBattleEvaluationService battleEvaluation = mock(MatchBattleEvaluationService.class);
    private final GameController controller = new GameController(game, redisState, battleEvaluation);

    @Test
    void createMatch_delegatesToService() {
        CreateMatchRequest req = new CreateMatchRequest(List.of(1L, 2L));
        MatchResponse expected = new MatchResponse(100, "WAITING", 1, List.of(1L, 2L), null);
        when(game.createMatch(req)).thenReturn(expected);

        MatchResponse response = controller.createMatch(req);

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void getMatch_delegatesToService() {
        MatchResponse expected = new MatchResponse(7, "WAITING", 1, List.of(10L, 20L), null);
        when(game.getMatch(7)).thenReturn(expected);

        assertThat(controller.getMatch(7)).isEqualTo(expected);
    }

    @Test
    void start_callsService() {
        controller.start(8);
        verify(game).startMatch(8);
    }

    @Test
    void getState_returnsRedisState() {
        when(redisState.getState(3)).thenReturn("state");
        assertThat(controller.getState(3)).isEqualTo("state");
    }

    @Test
    void getBoard_returnsRedisBoard() {
        when(redisState.getBoard(3)).thenReturn("board");
        assertThat(controller.getBoard(3)).isEqualTo("board");
    }

    @Test
    void getShop_returnsResponseForAuthenticatedUser() {
        ShopStateResponse expected = new ShopStateResponse(2, 100, 100, List.of(), List.of(), List.of(), new KingSquareResponse(4, 7), 1000L);
        when(game.getShopState(1, 11L)).thenReturn(expected);

        ShopStateResponse response = controller.getShop(1, auth("11"));

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void getShop_mapsIllegalArgumentToBadRequest() {
        when(game.getShopState(1, 11L)).thenThrow(new IllegalArgumentException("bad input"));

        assertThatThrownBy(() -> controller.getShop(1, auth("11")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void getShop_mapsIllegalStateToConflict() {
        when(game.getShopState(1, 11L)).thenThrow(new IllegalStateException("conflict"));

        assertThatThrownBy(() -> controller.getShop(1, auth("11")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void buyPiece_returnsResponse() {
        BuyPieceRequest req = new BuyPieceRequest("pawn", 0);
        BuyPieceResponse expected = new BuyPieceResponse("pawn", 2, 1, 0);
        when(game.buyPiece(1, 11L, req)).thenReturn(expected);

        BuyPieceResponse response = controller.buyPiece(1, req, auth("11"));

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void sellPiece_returnsResponse() {
        SellPieceRequest req = new SellPieceRequest(1, null, null);
        SellPieceResponse expected = new SellPieceResponse("pawn", 1, 2);
        when(game.sellPiece(1, 11L, req)).thenReturn(expected);

        SellPieceResponse response = controller.sellPiece(1, req, auth("11"));

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void placePieceFromBench_returnsNoContent() {
        PlacePieceRequest req = new PlacePieceRequest(0, 4, 6);

        ResponseEntity<Void> response = controller.placePieceFromBench(1, req, auth("11"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void moveBoardPiece_returnsNoContent() {
        MovePieceRequest req = new MovePieceRequest(1, 6, 2, 6);

        ResponseEntity<Void> response = controller.moveBoardPiece(1, req, auth("11"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void moveKing_returnsNoContent() {
        MoveKingRequest req = new MoveKingRequest(5, 7);

        ResponseEntity<Void> response = controller.moveKing(1, req, auth("11"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void evaluateBattleRound_returnsResponse() {
        BattleRoundEvaluationResponse expected = new BattleRoundEvaluationResponse(
                "fen",
                50,
                "white",
                11L,
                22L,
                true,
                List.of(),
                List.of(),
                new KingSquareResponse(4, 7),
                new KingSquareResponse(4, 7),
                List.of("e2e4"),
                100L,
                99,
                98,
                false,
                null
        );
        when(battleEvaluation.evaluateRound(1, 11L)).thenReturn(expected);

        BattleRoundEvaluationResponse response = controller.evaluateBattleRound(1, auth("11"));

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void evaluateBattleRound_mapsExceptions() {
        when(battleEvaluation.evaluateRound(1, 11L)).thenThrow(new IllegalStateException("busy"));

        assertThatThrownBy(() -> controller.evaluateBattleRound(1, auth("11")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void authenticatedUserIsRequired() {
        assertThatThrownBy(() -> controller.getShop(1, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Missing authenticated user");
                });
    }

    @Test
    void authenticatedUserIdMustBeNumeric() {
        assertThatThrownBy(() -> controller.getShop(1, auth("abc")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Invalid user id");
                });
    }

    private Authentication auth(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        return authentication;
    }
}
