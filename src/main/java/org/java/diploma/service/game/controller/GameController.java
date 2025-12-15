package org.java.diploma.service.game.controller;


import jakarta.validation.Valid;
import org.java.diploma.service.game.dto.CreateMatchRequest;
import org.java.diploma.service.game.dto.MatchResponse;
import org.java.diploma.service.game.service.GameService;
import org.java.diploma.service.game.service.GameStateRedisService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService game;
    private final GameStateRedisService redisState;

    public GameController(GameService game, GameStateRedisService redisState) {
        this.game = game;
        this.redisState = redisState;
    }

    // Called by Matchmaking Service when a match is formed
    @PostMapping("/matches")
    public MatchResponse createMatch(@Valid @RequestBody CreateMatchRequest req) {
        return game.createMatch(req);
    }

    @GetMapping("/matches/{matchId}")
    public MatchResponse getMatch(@PathVariable Integer matchId) {
        return game.getMatch(matchId);
    }

    @PostMapping("/matches/{matchId}/start")
    public void start(@PathVariable Integer matchId) {
        game.startMatch(matchId);
    }

    @GetMapping("/matches/{matchId}/state")
    public Object getState(@PathVariable Integer matchId) {
        return redisState.getState(matchId);
    }

    @GetMapping("/matches/{matchId}/board")
    public Object getBoard(@PathVariable Integer matchId) {
        return redisState.getBoard(matchId);
    }
}
