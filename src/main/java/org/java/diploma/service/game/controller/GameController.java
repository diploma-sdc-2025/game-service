package org.java.diploma.service.game.controller;

import jakarta.validation.Valid;
import org.java.diploma.service.game.dto.BattleRoundEvaluationResponse;
import org.java.diploma.service.game.dto.BuyPieceRequest;
import org.java.diploma.service.game.dto.BuyPieceResponse;
import org.java.diploma.service.game.dto.CreateMatchRequest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private static final Logger logger = LoggerFactory.getLogger(GameController.class);

    private static final String MATCH_CREATED = "Match created successfully: matchId={}";
    private static final String MATCH_RETRIEVED = "Match retrieved: matchId={}";
    private static final String MATCH_STARTED = "Match started: matchId={}";
    private static final String STATE_RETRIEVED = "Game state retrieved for matchId={}";
    private static final String BOARD_RETRIEVED = "Game board retrieved for matchId={}";
    private static final String CREATE_MATCH_REQUEST = "Received create match request for {} players";
    private static final String SHOP_RETRIEVED = "Shop state retrieved: matchId={}, userId={}";
    private static final String PIECE_BOUGHT = "Piece bought: matchId={}, userId={}, piece={}";
    private static final String PIECE_SOLD = "Piece sold: matchId={}, userId={}, piece={}";
    private static final String PIECE_PLACED = "Piece placed on board: matchId={}, userId={}";
    private static final String PIECE_MOVED = "Board piece moved: matchId={}, userId={}";
    private static final String KING_MOVED = "King moved: matchId={}, userId={}";
    private static final String BATTLE_EVAL = "Battle round evaluation: matchId={}, userId={}";

    private final GameService game;
    private final GameStateRedisService redisState;
    private final MatchBattleEvaluationService battleEvaluation;

    public GameController(GameService game,
                          GameStateRedisService redisState,
                          MatchBattleEvaluationService battleEvaluation) {
        this.game = game;
        this.redisState = redisState;
        this.battleEvaluation = battleEvaluation;
    }

    @PostMapping("/matches")
    public MatchResponse createMatch(@Valid @RequestBody CreateMatchRequest req) {
        logger.info(CREATE_MATCH_REQUEST, req.playerIds() != null ? req.playerIds().size() : 0);

        MatchResponse response = game.createMatch(req);

        logger.info(MATCH_CREATED, response.matchId());
        return response;
    }

    @GetMapping("/matches/{matchId}")
    public MatchResponse getMatch(@PathVariable Integer matchId) {
        logger.debug(MATCH_RETRIEVED, matchId);
        return game.getMatch(matchId);
    }

    @PostMapping("/matches/{matchId}/start")
    public void start(@PathVariable Integer matchId) {
        logger.info(MATCH_STARTED, matchId);
        game.startMatch(matchId);
    }

    @GetMapping("/matches/{matchId}/state")
    public Object getState(@PathVariable Integer matchId) {
        logger.debug(STATE_RETRIEVED, matchId);
        return redisState.getState(matchId);
    }

    @GetMapping("/matches/{matchId}/board")
    public Object getBoard(@PathVariable Integer matchId) {
        logger.debug(BOARD_RETRIEVED, matchId);
        return redisState.getBoard(matchId);
    }

    @GetMapping("/matches/{matchId}/shop")
    public ShopStateResponse getShop(@PathVariable Integer matchId, Authentication authentication) {
        Long userId = requireUserId(authentication);
        logger.debug(SHOP_RETRIEVED, matchId, userId);
        try {
            return game.getShopState(matchId, userId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/matches/{matchId}/shop/buy")
    public BuyPieceResponse buyPiece(@PathVariable Integer matchId,
                                     @Valid @RequestBody BuyPieceRequest req,
                                     Authentication authentication) {
        Long userId = requireUserId(authentication);
        try {
            BuyPieceResponse response = game.buyPiece(matchId, userId, req);
            logger.info(PIECE_BOUGHT, matchId, userId, response.piece());
            return response;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/matches/{matchId}/inventory/sell")
    public SellPieceResponse sellPiece(@PathVariable Integer matchId,
                                       @Valid @RequestBody SellPieceRequest req,
                                       Authentication authentication) {
        Long userId = requireUserId(authentication);
        try {
            SellPieceResponse response = game.sellPiece(matchId, userId, req);
            logger.info(PIECE_SOLD, matchId, userId, response.piece());
            return response;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/matches/{matchId}/inventory/place")
    public ResponseEntity<Void> placePieceFromBench(@PathVariable Integer matchId,
                                                    @Valid @RequestBody PlacePieceRequest req,
                                                    Authentication authentication) {
        Long userId = requireUserId(authentication);
        try {
            game.placePieceFromBench(matchId, userId, req);
            logger.info(PIECE_PLACED, matchId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/matches/{matchId}/inventory/move")
    public ResponseEntity<Void> moveBoardPiece(@PathVariable Integer matchId,
                                                @Valid @RequestBody MovePieceRequest req,
                                                Authentication authentication) {
        Long userId = requireUserId(authentication);
        try {
            game.moveBoardPiece(matchId, userId, req);
            logger.info(PIECE_MOVED, matchId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/matches/{matchId}/king/move")
    public ResponseEntity<Void> moveKing(@PathVariable Integer matchId,
                                        @Valid @RequestBody MoveKingRequest req,
                                        Authentication authentication) {
        Long userId = requireUserId(authentication);
        try {
            game.moveKing(matchId, userId, req);
            logger.info(KING_MOVED, matchId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/matches/{matchId}/battle/evaluate-round")
    public BattleRoundEvaluationResponse evaluateBattleRound(@PathVariable Integer matchId,
                                                             Authentication authentication) {
        Long userId = requireUserId(authentication);
        logger.info(BATTLE_EVAL, matchId, userId);
        try {
            return battleEvaluation.evaluateRound(matchId, userId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Missing authenticated user");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid user id in token");
        }
    }
}