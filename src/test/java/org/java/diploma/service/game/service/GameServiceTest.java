package org.java.diploma.service.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java.diploma.service.game.dto.BattleRoundEvaluationResponse;
import org.java.diploma.service.game.dto.BoardPieceResponse;
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
import org.java.diploma.service.game.entity.Match;
import org.java.diploma.service.game.entity.MatchPlayer;
import org.java.diploma.service.game.entity.Piece;
import org.java.diploma.service.game.entity.PlayerInventory;
import org.java.diploma.service.game.entity.PlayerResources;
import org.java.diploma.service.game.repository.MatchPlayerRepository;
import org.java.diploma.service.game.repository.MatchRepository;
import org.java.diploma.service.game.repository.PieceRepository;
import org.java.diploma.service.game.repository.PlayerInventoryRepository;
import org.java.diploma.service.game.repository.PlayerResourcesRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameServiceTest {

    @Test
    void createMatch_createsMatchPlayersResources_andInitializesRedisState() {
        var deps = deps();
        when(deps.matches.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(123);
            return m;
        });
        var sut = deps.sut();

        MatchResponse res = sut.createMatch(new CreateMatchRequest(List.of(10L, 20L, 30L)));

        assertThat(res.matchId()).isEqualTo(123);
        assertThat(res.status()).isEqualTo("WAITING");
        verify(deps.redisState).initMatchState(123);
        ArgumentCaptor<MatchPlayer> mpCaptor = ArgumentCaptor.forClass(MatchPlayer.class);
        verify(deps.matchPlayers, times(3)).save(mpCaptor.capture());
        assertThat(mpCaptor.getAllValues())
                .extracting(MatchPlayer::getMatchId, MatchPlayer::getUserId)
                .containsExactly(tuple(123, 10L), tuple(123, 20L), tuple(123, 30L));
        verify(deps.resources, times(3)).save(any(PlayerResources.class));
    }

    @Test
    void getMatch_throwsWhenNotFound() {
        var deps = deps();
        when(deps.matches.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deps.sut().getMatch(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Match not found");
    }

    @Test
    void startMatch_updatesWaitingToInProgress() {
        var deps = deps();
        Match m = new Match();
        m.setId(1);
        m.setStatus("WAITING");
        when(deps.matches.findById(1)).thenReturn(Optional.of(m));

        deps.sut().startMatch(1);

        assertThat(m.getStatus()).isEqualTo("IN_PROGRESS");
        verify(deps.matches).save(m);
    }

    @Test
    void buyPiece_successWithProvidedSlot() {
        var deps = deps();
        when(deps.matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);
        when(deps.pieces.findByNameIgnoreCase("Pawn")).thenReturn(Optional.of(piece(101, "Pawn", 1)));
        when(deps.resources.findByMatchIdAndUserId(1, 10L)).thenReturn(Optional.of(resources(1, 10L, 5, 100)));
        when(deps.inventory.existsByMatchIdAndUserIdAndPositionXAndPositionY(1, 10L, 3, 8)).thenReturn(false);

        BuyPieceResponse response = deps.sut().buyPiece(1, 10L, new BuyPieceRequest("pawn", 3));

        assertThat(response).isEqualTo(new BuyPieceResponse("pawn", 5, 4, 3));
        verify(deps.inventory).save(any(PlayerInventory.class));
    }

    @Test
    void buyPiece_autoSlotAndBenchFullBranches() {
        var deps = deps();
        when(deps.matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);
        when(deps.pieces.findByNameIgnoreCase("Pawn")).thenReturn(Optional.of(piece(101, "Pawn", 1)));
        when(deps.resources.findByMatchIdAndUserId(1, 10L)).thenReturn(Optional.of(resources(1, 10L, 3, 100)));
        when(deps.inventory.existsByMatchIdAndUserIdAndPositionXAndPositionY(1, 10L, 0, 8)).thenReturn(true);
        when(deps.inventory.existsByMatchIdAndUserIdAndPositionXAndPositionY(1, 10L, 1, 8)).thenReturn(false);

        BuyPieceResponse response = deps.sut().buyPiece(1, 10L, new BuyPieceRequest("pawn", null));
        assertThat(response.slot()).isEqualTo(1);

        for (int i = 0; i < 8; i++) {
            when(deps.inventory.existsByMatchIdAndUserIdAndPositionXAndPositionY(2, 10L, i, 8)).thenReturn(true);
        }
        when(deps.matchPlayers.existsByMatchIdAndUserId(2, 10L)).thenReturn(true);
        when(deps.resources.findByMatchIdAndUserId(2, 10L)).thenReturn(Optional.of(resources(2, 10L, 3, 100)));
        assertThatThrownBy(() -> deps.sut().buyPiece(2, 10L, new BuyPieceRequest("pawn", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bench is full");
    }

    @Test
    void placePieceFromBench_successAndPawnRestriction() {
        var deps = deps();
        PlayerInventory benchPawn = benchItem(1, 10L, 101, 2);
        when(deps.matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);
        when(deps.inventory.findByMatchIdAndUserIdAndPositionXAndPositionY(1, 10L, 2, 8)).thenReturn(Optional.of(benchPawn));
        when(deps.pieces.findById(101)).thenReturn(Optional.of(piece(101, "Pawn", 1)));
        when(deps.redisState.getKingSquare(1, 10L)).thenReturn(new KingSquareResponse(4, 7));
        when(deps.inventory.existsByMatchIdAndUserIdAndPositionXAndPositionYAndIsOnBoardIsTrue(1, 10L, 3, 6)).thenReturn(false);

        deps.sut().placePieceFromBench(1, 10L, new PlacePieceRequest(2, 3, 6));
        verify(deps.inventory).save(benchPawn);

        when(deps.inventory.findByMatchIdAndUserIdAndPositionXAndPositionY(1, 10L, 2, 8))
                .thenReturn(Optional.of(benchItem(1, 10L, 101, 2)));
        assertThatThrownBy(() -> deps.sut().placePieceFromBench(1, 10L, new PlacePieceRequest(2, 3, 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pawns may only be on ranks 2–4");
    }

    @Test
    void sellPiece_validatesModesAndSellsFromBench() {
        var deps = deps();
        when(deps.matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);

        assertThatThrownBy(() -> deps.sut().sellPiece(1, 10L, new SellPieceRequest(1, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> deps.sut().sellPiece(1, 10L, new SellPieceRequest(null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        PlayerInventory bench = benchItem(1, 10L, 101, 1);
        when(deps.inventory.findByMatchIdAndUserIdAndPositionXAndPositionY(1, 10L, 1, 8)).thenReturn(Optional.of(bench));
        when(deps.pieces.findById(101)).thenReturn(Optional.of(piece(101, "Pawn", 1)));
        when(deps.resources.findByMatchIdAndUserId(1, 10L)).thenReturn(Optional.of(resources(1, 10L, 2, 100)));

        SellPieceResponse response = deps.sut().sellPiece(1, 10L, new SellPieceRequest(1, null, null));
        assertThat(response.moneyAfter()).isEqualTo(3);
        verify(deps.inventory).delete(bench);
    }

    @Test
    void moveBoardPiece_handlesNoopAndSuccess() {
        var deps = deps();
        when(deps.matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);

        deps.sut().moveBoardPiece(1, 10L, new MovePieceRequest(1, 6, 1, 6));
        verify(deps.inventory, never()).save(any(PlayerInventory.class));

        PlayerInventory boardPiece = boardItem(1, 10L, 101, 1, 6);
        when(deps.inventory.findByMatchIdAndUserIdAndPositionXAndPositionY(1, 10L, 1, 6)).thenReturn(Optional.of(boardPiece));
        when(deps.pieces.findById(101)).thenReturn(Optional.of(piece(101, "Knight", 3)));
        when(deps.redisState.getKingSquare(1, 10L)).thenReturn(new KingSquareResponse(4, 7));
        when(deps.inventory.existsByMatchIdAndUserIdAndPositionXAndPositionYAndIsOnBoardIsTrue(1, 10L, 2, 5)).thenReturn(false);

        deps.sut().moveBoardPiece(1, 10L, new MovePieceRequest(1, 6, 2, 5));
        assertThat(boardPiece.getPositionX()).isEqualTo(2);
        verify(deps.inventory).save(boardPiece);
    }

    @Test
    void moveKing_validatesAndPersists() {
        var deps = deps();
        when(deps.matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);
        when(deps.redisState.getKingSquare(1, 10L)).thenReturn(new KingSquareResponse(4, 7));
        when(deps.inventory.existsByMatchIdAndUserIdAndPositionXAndPositionYAndIsOnBoardIsTrue(1, 10L, 5, 7)).thenReturn(false);

        deps.sut().moveKing(1, 10L, new MoveKingRequest(5, 7));
        verify(deps.redisState).setKingSquare(1, 10L, 5, 7);

        assertThatThrownBy(() -> deps.sut().moveKing(1, 10L, new MoveKingRequest(5, 3)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getShopState_buildsStructuredResponse() {
        var deps = deps();
        when(deps.matchPlayers.existsByMatchIdAndUserId(1, 10L)).thenReturn(true);
        when(deps.resources.findByMatchIdAndUserId(1, 10L)).thenReturn(Optional.of(resources(1, 10L, 4, 77)));
        when(deps.inventory.findAllByMatchIdAndUserId(1, 10L)).thenReturn(List.of(benchItem(1, 10L, 101, 0), boardItem(1, 10L, 102, 2, 6)));
        when(deps.pieces.findById(101)).thenReturn(Optional.of(piece(101, "Pawn", 1)));
        when(deps.pieces.findById(102)).thenReturn(Optional.of(piece(102, "Knight", 3)));
        when(deps.pieces.findByNameIgnoreCase("Pawn")).thenReturn(Optional.of(piece(101, "Pawn", 1)));
        when(deps.pieces.findByNameIgnoreCase("Knight")).thenReturn(Optional.of(piece(102, "Knight", 3)));
        when(deps.pieces.findByNameIgnoreCase("Bishop")).thenReturn(Optional.of(piece(103, "Bishop", 3)));
        when(deps.pieces.findByNameIgnoreCase("Rook")).thenReturn(Optional.of(piece(104, "Rook", 5)));
        when(deps.pieces.findByNameIgnoreCase("Queen")).thenReturn(Optional.of(piece(105, "Queen", 8)));
        when(deps.redisState.getKingSquare(1, 10L)).thenReturn(new KingSquareResponse(4, 7));
        when(deps.redisState.ensureAndGetShopPhaseEndsAtMillis(1)).thenReturn(999L);

        ShopStateResponse shop = deps.sut().getShopState(1, 10L);
        assertThat(shop.money()).isEqualTo(4);
        assertThat(shop.bench()).hasSize(1);
        assertThat(shop.board()).hasSize(1);
        assertThat(shop.shopPhaseEndsAt()).isEqualTo(999L);
    }

    @Test
    void finalizeBattleRoundEvaluation_usesCacheWhenPresent() throws Exception {
        var deps = deps();
        when(deps.matches.findByIdForUpdate(1)).thenReturn(Optional.of(new Match()));
        BattleRoundEvaluationResponse cached = new BattleRoundEvaluationResponse(
                "fen", 10, "white", 10L, 20L, false,
                List.of(), List.of(), new KingSquareResponse(4, 7), new KingSquareResponse(4, 7),
                List.of("e2e4"), 1L, 100, 100);
        when(deps.redisState.getCachedBattleEval(1, 3)).thenReturn(Optional.of(new ObjectMapper().writeValueAsString(cached)));

        BattleRoundEvaluationResponse out = deps.sut().finalizeBattleRoundEvaluation(
                1, 3, 10L, 10L, 20L, "fen", 10, "white",
                List.of(), List.of(), new KingSquareResponse(4, 7), new KingSquareResponse(4, 7), List.of("e2e4"));

        assertThat(out.currentUserIsWhite()).isTrue();
    }

    @Test
    void finalizeBattleRoundEvaluation_uncachedAppliesOutcomeAndRegistersAfterCommit() {
        var deps = deps();
        when(deps.matches.findByIdForUpdate(1)).thenReturn(Optional.of(new Match()));
        when(deps.redisState.getCachedBattleEval(1, 5)).thenReturn(Optional.empty());
        PlayerResources white = resources(1, 10L, 3, 100);
        PlayerResources black = resources(1, 20L, 3, 100);
        when(deps.resources.findByMatchIdAndUserId(1, 10L)).thenReturn(Optional.of(white));
        when(deps.resources.findByMatchIdAndUserId(1, 20L)).thenReturn(Optional.of(black));

        TransactionSynchronizationManager.initSynchronization();
        try {
            BattleRoundEvaluationResponse out = deps.sut().finalizeBattleRoundEvaluation(
                    1, 5, 10L, 10L, 20L, "fen", 250, "white",
                    List.of(new BoardPieceResponse(1, 6, "pawn")),
                    List.of(new BoardPieceResponse(6, 1, "pawn")),
                    new KingSquareResponse(4, 7), new KingSquareResponse(4, 7), List.of("e2e4"));

            assertThat(out.blackHp()).isLessThan(100);
            assertThat(white.getGold()).isEqualTo(5);
            assertThat(black.getGold()).isEqualTo(5);

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(deps.redisState).setCachedBattleEval(eq(1L), eq(5), anyString());
            verify(deps.redisState).setLastBattleEval(eq(1L), eq(5), anyString());
            verify(deps.redisState).incrementShopRoundAfterBattle(1L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private record Deps(
            MatchRepository matches,
            MatchPlayerRepository matchPlayers,
            PlayerResourcesRepository resources,
            PieceRepository pieces,
            PlayerInventoryRepository inventory,
            GameStateRedisService redisState
    ) {
        GameService sut() {
            return new GameService(matches, matchPlayers, resources, pieces, inventory, redisState, new ObjectMapper());
        }
    }

    private Deps deps() {
        return new Deps(
                mock(MatchRepository.class),
                mock(MatchPlayerRepository.class),
                mock(PlayerResourcesRepository.class),
                mock(PieceRepository.class),
                mock(PlayerInventoryRepository.class),
                mock(GameStateRedisService.class)
        );
    }

    private Piece piece(int id, String name, int cost) {
        Piece p = new Piece();
        p.setId(id);
        p.setName(name);
        p.setCostGold(cost);
        return p;
    }

    private PlayerResources resources(int matchId, long userId, int gold, int hp) {
        PlayerResources pr = new PlayerResources();
        pr.setMatchId(matchId);
        pr.setUserId(userId);
        pr.setGold(gold);
        pr.setHp(hp);
        return pr;
    }

    private PlayerInventory benchItem(int matchId, long userId, int pieceId, int slot) {
        PlayerInventory item = new PlayerInventory();
        item.setMatchId(matchId);
        item.setUserId(userId);
        item.setPieceId(pieceId);
        item.setPositionX(slot);
        item.setPositionY(8);
        item.setOnBoard(false);
        return item;
    }

    private PlayerInventory boardItem(int matchId, long userId, int pieceId, int x, int y) {
        PlayerInventory item = benchItem(matchId, userId, pieceId, x);
        item.setPositionY(y);
        item.setOnBoard(true);
        return item;
    }
}
