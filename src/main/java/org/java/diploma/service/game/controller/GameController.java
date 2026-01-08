package org.java.diploma.service.game.controller;

import jakarta.validation.Valid;
import org.java.diploma.service.game.dto.CreateMatchRequest;
import org.java.diploma.service.game.dto.MatchResponse;
import org.java.diploma.service.game.service.GameService;
import org.java.diploma.service.game.service.GameStateRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

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

    private final GameService game;
    private final GameStateRedisService redisState;

    public GameController(GameService game, GameStateRedisService redisState) {
        this.game = game;
        this.redisState = redisState;
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
}