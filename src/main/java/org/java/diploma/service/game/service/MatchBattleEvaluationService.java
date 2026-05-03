package org.java.diploma.service.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java.diploma.service.game.chess.ChessFenBuilder;
import org.java.diploma.service.game.dto.BattleEngineEvaluateResponse;
import org.java.diploma.service.game.dto.BattleRoundEvaluationResponse;
import org.java.diploma.service.game.dto.BoardPieceResponse;
import org.java.diploma.service.game.dto.KingSquareResponse;
import org.java.diploma.service.game.entity.MatchPlayer;
import org.java.diploma.service.game.entity.PlayerInventory;
import org.java.diploma.service.game.repository.MatchPlayerRepository;
import org.java.diploma.service.game.repository.PieceRepository;
import org.java.diploma.service.game.repository.PlayerInventoryRepository;
import org.java.diploma.service.game.config.BattleClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class MatchBattleEvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(MatchBattleEvaluationService.class);

    /**
     * Picks White pseudorandomly but deterministically from {@code matchId}, the current {@code shopBattleRound}
     * (same key as battle-eval cache), and the two user ids so both clients agree. Changes when the round advances
     * after each battle, so sides are not fixed for the whole match. Argument order does not matter.
     */
    static long resolveWhiteUserId(Integer matchId, int shopBattleRound, long userIdA, long userIdB) {
        long low = Math.min(userIdA, userIdB);
        long high = Math.max(userIdA, userIdB);
        int h = Objects.hash(matchId, shopBattleRound, low, high);
        return Math.floorMod(h, 2) == 0 ? low : high;
    }

    private final MatchPlayerRepository matchPlayers;
    private final PlayerInventoryRepository inventory;
    private final PieceRepository pieces;
    private final GameStateRedisService redisState;
    private final RestClient battleRestClient;
    private final GameService gameService;
    private final ObjectMapper objectMapper;

    public MatchBattleEvaluationService(
            MatchPlayerRepository matchPlayers,
            PlayerInventoryRepository inventory,
            PieceRepository pieces,
            GameStateRedisService redisState,
            @Qualifier(BattleClientConfig.BATTLE_REST_CLIENT) RestClient battleRestClient,
            GameService gameService,
            ObjectMapper objectMapper
    ) {
        this.matchPlayers = matchPlayers;
        this.inventory = inventory;
        this.pieces = pieces;
        this.redisState = redisState;
        this.battleRestClient = battleRestClient;
        this.gameService = gameService;
        this.objectMapper = objectMapper;
    }

    public BattleRoundEvaluationResponse evaluateRound(Integer matchId, Long currentUserId) {
        if (!matchPlayers.existsByMatchIdAndUserId(matchId, currentUserId)) {
            throw new IllegalArgumentException("Player is not part of this match");
        }

        List<Long> ordered = matchPlayers.findAllByMatchId(matchId).stream()
                .map(MatchPlayer::getUserId)
                .sorted()
                .toList();
        if (ordered.size() != 2) {
            throw new IllegalStateException("Battle evaluation requires exactly two players in the match");
        }

        int redisRound = redisState.getShopRound(matchId);
        long userIdLeft = ordered.get(0);
        long userIdRight = ordered.get(1);
        long whiteUserId = resolveWhiteUserId(matchId, redisRound, userIdLeft, userIdRight);
        long blackUserId = whiteUserId == userIdLeft ? userIdRight : userIdLeft;
        logger.debug(
                "Battle coloring: matchId={} redisRound={} whiteUserId={} blackUserId={}",
                matchId,
                redisRound,
                whiteUserId,
                blackUserId);

        List<BoardPieceResponse> whiteBoard = loadOnBoardPieces(matchId, whiteUserId);
        List<BoardPieceResponse> blackBoardRaw = loadOnBoardPieces(matchId, blackUserId);

        redisState.initPlayerKing(matchId, whiteUserId);
        redisState.initPlayerKing(matchId, blackUserId);
        KingSquareResponse whiteKing = redisState.getKingSquare(matchId, whiteUserId);
        KingSquareResponse blackKingRaw = redisState.getKingSquare(matchId, blackUserId);
        if (whiteKing == null || blackKingRaw == null) {
            throw new IllegalStateException("King positions not available for both players");
        }

        // Convert Black setup to opposite side for battle resolution/preview.
        List<BoardPieceResponse> blackBoard = blackBoardRaw.stream()
                .map(this::mirrorForBlackSide)
                .toList();
        KingSquareResponse blackKing = mirrorForBlackSide(blackKingRaw);

        String fen = ChessFenBuilder.build(whiteBoard, whiteKing, blackBoard, blackKing);

        var cached = redisState.getCachedBattleEval(matchId, redisRound);
        if (cached.isPresent()) {
            try {
                BattleRoundEvaluationResponse parsed =
                        objectMapper.readValue(cached.get(), BattleRoundEvaluationResponse.class);
                BattleRoundEvaluationResponse out = parsed.forViewer(currentUserId);
                redisState.markBattleViewedByUser(matchId, redisRound, currentUserId);
                return out;
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Cached battle evaluation is corrupt", e);
            }
        }

        // Refresh during battle can happen after shop round already advanced.
        // In that case, return the last published battle snapshot if it's still in-view window.
        var lastBattle = redisState.getLastBattleEval(matchId);
        if (lastBattle.isPresent()) {
            try {
                BattleRoundEvaluationResponse parsed =
                        objectMapper.readValue(lastBattle.get().json(), BattleRoundEvaluationResponse.class);
                if (parsed.battleViewEndsAt() > System.currentTimeMillis()) {
                    BattleRoundEvaluationResponse out = parsed.forViewer(currentUserId);
                    redisState.markBattleViewedByUser(matchId, lastBattle.get().round(), currentUserId);
                    return out;
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Last battle evaluation cache is corrupt", e);
            }
        }

        gameService.ensureMatchInProgress(matchId);

        BattleEngineEvaluateResponse engine = callBattleEvaluate(fen);

        BattleRoundEvaluationResponse out = gameService.finalizeBattleRoundEvaluation(
                matchId,
                redisRound,
                currentUserId,
                whiteUserId,
                blackUserId,
                fen,
                engine.centipawns(),
                engine.advantage(),
                whiteBoard,
                blackBoard,
                whiteKing,
                blackKing,
                engine.principalVariation()
        );
        redisState.markBattleViewedByUser(matchId, redisRound, currentUserId);
        return out;
    }

    private BattleEngineEvaluateResponse callBattleEvaluate(String fen) {
        try {
            BattleEngineEvaluateResponse res = battleRestClient.post()
                    .uri("/api/battle/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("fen", fen))
                    .retrieve()
                    .body(BattleEngineEvaluateResponse.class);
            if (res == null) {
                throw new IllegalStateException("Battle engine returned an empty body");
            }
            return res;
        } catch (RestClientException e) {
            logger.error("Battle service evaluate failed", e);
            throw new IllegalStateException("Battle engine unavailable: " + e.getMessage());
        }
    }

    private List<BoardPieceResponse> loadOnBoardPieces(Integer matchId, Long userId) {
        // Runtime Redis board is the source of truth during shop phase.
        if (redisState.hasAnyPlayerBoardData(matchId, userId)) {
            return redisState.getPlayerBoard(matchId, userId).stream()
                    .map(p -> new BoardPieceResponse(p.x(), p.y(), normalizePieceKey(p.pieceKey())))
                    .toList();
        }

        // Fallback for cold-start / migration scenarios.
        List<BoardPieceResponse> boardPieces = new ArrayList<>();
        for (PlayerInventory item : inventory.findAllByMatchIdAndUserId(matchId, userId)) {
            if (!item.isOnBoard()) {
                continue;
            }
            pieces.findById(item.getPieceId()).ifPresent(pe ->
                    boardPieces.add(new BoardPieceResponse(
                            item.getPositionX(),
                            item.getPositionY(),
                            normalizePieceKey(pe.getName()))));
        }
        return boardPieces;
    }

    private BoardPieceResponse mirrorForBlackSide(BoardPieceResponse p) {
        return new BoardPieceResponse(7 - p.x(), 7 - p.y(), p.piece());
    }

    private KingSquareResponse mirrorForBlackSide(KingSquareResponse king) {
        return new KingSquareResponse(7 - king.x(), 7 - king.y());
    }

    private static String normalizePieceKey(String piece) {
        return piece == null ? "" : piece.trim().toLowerCase();
    }
}
