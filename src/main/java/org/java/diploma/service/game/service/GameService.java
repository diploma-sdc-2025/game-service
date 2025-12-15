package org.java.diploma.service.game.service;


import org.java.diploma.service.game.dto.CreateMatchRequest;
import org.java.diploma.service.game.dto.MatchResponse;
import org.java.diploma.service.game.entity.Match;
import org.java.diploma.service.game.entity.MatchPlayer;
import org.java.diploma.service.game.entity.PlayerResources;
import org.java.diploma.service.game.repository.MatchPlayerRepository;
import org.java.diploma.service.game.repository.MatchRepository;
import org.java.diploma.service.game.repository.PlayerResourcesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GameService {

    private final MatchRepository matches;
    private final MatchPlayerRepository matchPlayers;
    private final PlayerResourcesRepository resources;
    private final GameStateRedisService redisState;

    public GameService(MatchRepository matches,
                       MatchPlayerRepository matchPlayers,
                       PlayerResourcesRepository resources,
                       GameStateRedisService redisState) {
        this.matches = matches;
        this.matchPlayers = matchPlayers;
        this.resources = resources;
        this.redisState = redisState;
    }

    @Transactional
    public MatchResponse createMatch(CreateMatchRequest req) {
        Match m = new Match();
        m.setStatus("WAITING");
        m = matches.save(m);

        for (Long userId : req.playerIds()) {
            MatchPlayer mp = new MatchPlayer();
            mp.setMatchId(m.getId());
            mp.setUserId(userId);
            matchPlayers.save(mp);

            PlayerResources pr = new PlayerResources();
            pr.setMatchId(m.getId());
            pr.setUserId(userId);
            pr.setGold(0);
            pr.setLevel(1);
            pr.setExperience(0);
            resources.save(pr);
        }

        redisState.initMatchState(m.getId());

        return new MatchResponse(m.getId(), m.getStatus(), m.getCurrentRound(), req.playerIds());
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(Integer matchId) {
        Match m = matches.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        List<Long> players = matchPlayers.findAllByMatchId(matchId)
                .stream().map(MatchPlayer::getUserId).toList();

        return new MatchResponse(m.getId(), m.getStatus(), m.getCurrentRound(), players);
    }

    @Transactional
    public void startMatch(Integer matchId) {
        Match m = matches.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        if (!"WAITING".equals(m.getStatus())) {
            throw new IllegalStateException("Match is not in WAITING state");
        }

        m.setStatus("IN_PROGRESS");
        matches.save(m);
    }
}
