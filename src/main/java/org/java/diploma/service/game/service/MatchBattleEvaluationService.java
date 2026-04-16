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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

@Service
public class MatchBattleEvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(MatchBattleEvaluationService.class);

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
            RestClient battleRestClient,
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

        long whiteUserId = ordered.get(0);
        long blackUserId = ordered.get(1);

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

        int redisRound = redisState.getShopRound(matchId);
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
