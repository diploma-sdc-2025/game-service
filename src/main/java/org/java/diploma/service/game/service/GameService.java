package org.java.diploma.service.game.service;

import org.java.diploma.service.game.dto.CreateMatchRequest;
import org.java.diploma.service.game.dto.MatchResponse;
import org.java.diploma.service.game.entity.Match;
import org.java.diploma.service.game.entity.MatchPlayer;
import org.java.diploma.service.game.entity.PlayerResources;
import org.java.diploma.service.game.repository.MatchPlayerRepository;
import org.java.diploma.service.game.repository.MatchRepository;
import org.java.diploma.service.game.repository.PlayerResourcesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    private static final String LOG_CREATING_MATCH = "Creating new match with {} players";
    private static final String LOG_RETRIEVING_MATCH = "Retrieving match: matchId={}";
    private static final String LOG_STARTING_MATCH = "Starting match: matchId={}";

    private static final String ERROR_MATCH_NOT_FOUND_MESSAGE = "Match not found: ";
    private static final String ERROR_MATCH_NOT_WAITING_MESSAGE = "Match is not in WAITING state";
    private static final String ERROR_MATCH_NOT_FOUND = "Match not found: matchId={}";
    private static final String ERROR_MATCH_NOT_WAITING = "Match is not in WAITING state: matchId={}, status={}";

    private static final String MATCH_CREATED = "Match created: matchId={}, playerCount={}";
    private static final String PLAYER_ADDED_TO_MATCH = "Player added to match: matchId={}, userId={}";
    private static final String PLAYER_RESOURCES_INITIALIZED = "Player resources initialized: matchId={}, userId={}";
    private static final String MATCH_STATE_INITIALIZED = "Match state initialized in Redis: matchId={}";
    private static final String MATCH_RETRIEVED = "Match retrieved: matchId={}";
    private static final String MATCH_STARTED = "Match started: matchId={}";

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
        logger.info(LOG_CREATING_MATCH, req.playerIds().size());

        Match m = new Match();
        m.setStatus(Match.STATUS_WAITING);
        m = matches.save(m);

        logger.info(MATCH_CREATED, m.getId(), req.playerIds().size());

        for (Long userId : req.playerIds()) {
            MatchPlayer mp = new MatchPlayer();
            mp.setMatchId(m.getId());
            mp.setUserId(userId);
            matchPlayers.save(mp);
            logger.debug(PLAYER_ADDED_TO_MATCH, m.getId(), userId);

            PlayerResources pr = new PlayerResources();
            pr.setMatchId(m.getId());
            pr.setUserId(userId);
            pr.setGold(PlayerResources.DEFAULT_GOLD);
            pr.setLevel(PlayerResources.DEFAULT_LEVEL);
            pr.setExperience(PlayerResources.DEFAULT_EXPERIENCE);
            resources.save(pr);
            logger.debug(PLAYER_RESOURCES_INITIALIZED, m.getId(), userId);
        }

        redisState.initMatchState(m.getId());
        logger.info(MATCH_STATE_INITIALIZED, m.getId());

        return new MatchResponse(m.getId(), m.getStatus(), m.getCurrentRound(), req.playerIds());
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(Integer matchId) {
        logger.debug(LOG_RETRIEVING_MATCH, matchId);

        Match m = matches.findById(matchId)
                .orElseThrow(() -> {
                    logger.error(ERROR_MATCH_NOT_FOUND, matchId);
                    return new IllegalArgumentException(ERROR_MATCH_NOT_FOUND_MESSAGE + matchId);
                });

        List<Long> players = matchPlayers.findAllByMatchId(matchId)
                .stream().map(MatchPlayer::getUserId).toList();

        logger.debug(MATCH_RETRIEVED, matchId);
        return new MatchResponse(m.getId(), m.getStatus(), m.getCurrentRound(), players);
    }

    @Transactional
    public void startMatch(Integer matchId) {
        logger.info(LOG_STARTING_MATCH, matchId);

        Match m = matches.findById(matchId)
                .orElseThrow(() -> {
                    logger.error(ERROR_MATCH_NOT_FOUND, matchId);
                    return new IllegalArgumentException(ERROR_MATCH_NOT_FOUND_MESSAGE + matchId);
                });

        m.start();
        matches.save(m);

        logger.info(MATCH_STARTED, matchId);
    }
}