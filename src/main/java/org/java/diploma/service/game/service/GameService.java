package org.java.diploma.service.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java.diploma.service.game.analytics.AnalyticsEventPublisher;
import org.java.diploma.service.game.dto.BattleRoundEvaluationResponse;
import org.java.diploma.service.game.dto.BattleRoundHpSnapshot;
import org.java.diploma.service.game.dto.BenchSlotResponse;
import org.java.diploma.service.game.dto.BoardPieceResponse;
import org.java.diploma.service.game.dto.CreateMatchRequest;
import org.java.diploma.service.game.dto.BuyPieceRequest;
import org.java.diploma.service.game.dto.BuyPieceResponse;
import org.java.diploma.service.game.dto.KingSquareResponse;
import org.java.diploma.service.game.dto.MatchResponse;
import org.java.diploma.service.game.dto.MoveKingRequest;
import org.java.diploma.service.game.dto.MovePieceRequest;
import org.java.diploma.service.game.dto.PlacePieceRequest;
import org.java.diploma.service.game.dto.SellPieceRequest;
import org.java.diploma.service.game.dto.SellPieceResponse;
import org.java.diploma.service.game.dto.ShopItemResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    private static final String LOG_CREATING_MATCH = "Creating new match with {} players";
    private static final String LOG_RETRIEVING_MATCH = "Retrieving match: matchId={}";
    private static final String LOG_STARTING_MATCH = "Starting match: matchId={}";
    private static final String LOG_GET_SHOP = "Getting shop state: matchId={}, userId={}";
    private static final String LOG_BUY_PIECE = "Buying piece: matchId={}, userId={}, piece={}";
    private static final String LOG_PLACE_PIECE = "Placing piece from bench: matchId={}, userId={}, benchSlot={}, squareX={}, squareY={}";
    private static final String LOG_MOVE_PIECE = "Moving board piece: matchId={}, userId={}, from=({},{}), to=({},{})";
    private static final String LOG_SELL_PIECE = "Piece sold: matchId={}, userId={}, piece={}, refund={}";

    /** Bench rows use this Y so they never collide with board squares (0–7). */
    private static final int BENCH_POSITION_Y = 8;

    /**
     * White POV: UI row 0 = rank 8, row 7 = rank 1. Pawns may only sit on chess ranks 2–4 → rows 4–6.
     */
    private static final int PAWN_RANK_MIN_ROW = 4;
    private static final int PAWN_RANK_MAX_ROW = 6;

    /** Any file a–h is allowed for king movement → columns 0–7. */
    private static final int KING_LANE_MIN_COL = 0;
    private static final int KING_LANE_MAX_COL = 7;
    /** White POV rows for ranks 1–4 only. Ranks 5–8 (rows 0–3) are blocked. */
    private static final int KING_RANK_MIN_ROW = 4;
    private static final int KING_RANK_MAX_ROW = 7;

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
    private static final String LOG_BATTLE_HP = "Battle HP update: matchId={}, centipawns={}, loserUserId={}, damage={}";
    private static final String LOG_BATTLE_PAWNS = "Battle round pawn income: matchId={}, userId={}, +{}";

    /**
     * Max HP removed per round after converting eval to pawns ({@code round(|cp|/100)}). The eval bar can still
     * show higher advantages; actual HP loss is capped here (currently 10 per round).
     */
    private static final int BATTLE_HP_MAX_DAMAGE_PAWNS_ROUNDED = 10;
    /** Each side receives this many pawns (gold) when a battle round is resolved. */
    private static final int BATTLE_ROUND_PAWNS_PER_SIDE = 2;
    /** Shared battle presentation window so both clients can replay and transition in lockstep. */
    private static final long BATTLE_VIEW_BASE_DELAY_MS = 50L;
    private static final long BATTLE_VIEW_STEP_MS = 1000L;
    private static final long BATTLE_VIEW_END_PAUSE_MS = 2500L;
    private static final int BATTLE_VIEW_MAX_PLIES = 20;
    private static final long BATTLE_VIEW_SAFETY_BUFFER_MS = 750L;

    private final MatchRepository matches;
    private final MatchPlayerRepository matchPlayers;
    private final PlayerResourcesRepository resources;
    private final PieceRepository pieces;
    private final PlayerInventoryRepository inventory;
    private final GameStateRedisService redisState;
    private final ObjectMapper objectMapper;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final MatchRatingNotifier matchRatingNotifier;

    public GameService(MatchRepository matches,
                       MatchPlayerRepository matchPlayers,
                       PlayerResourcesRepository resources,
                       PieceRepository pieces,
                       PlayerInventoryRepository inventory,
                       GameStateRedisService redisState,
                       ObjectMapper objectMapper,
                       AnalyticsEventPublisher analyticsEventPublisher,
                       MatchRatingNotifier matchRatingNotifier) {
        this.matches = matches;
        this.matchPlayers = matchPlayers;
        this.resources = resources;
        this.pieces = pieces;
        this.inventory = inventory;
        this.redisState = redisState;
        this.objectMapper = objectMapper;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.matchRatingNotifier = matchRatingNotifier;
    }

    @Transactional
    public MatchResponse createMatch(CreateMatchRequest req) {
        logger.info(LOG_CREATING_MATCH, req.playerIds().size());

        Match m = new Match();
        // Matchmaking pairs two ready players, so a match is immediately playable on creation —
        // there is no real WAITING period. Marking it IN_PROGRESS here means subsequent buy/move
        // requests pass ensureMatchInProgress without needing a separate /start call.
        m.setStatus(Match.STATUS_WAITING);
        m = matches.save(m);
        m.start();
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
            // Start with 2 so players can buy a pawn immediately.
            pr.setGold(2);
            pr.setHp(PlayerResources.DEFAULT_HP);
            pr.setLevel(PlayerResources.DEFAULT_LEVEL);
            pr.setExperience(PlayerResources.DEFAULT_EXPERIENCE);
            resources.save(pr);
            logger.debug(PLAYER_RESOURCES_INITIALIZED, m.getId(), userId);

            redisState.initPlayerResourcesIfAbsent(m.getId(), userId, pr.getGold(), pr.getHp());
            redisState.initPlayerKing(m.getId(), userId);
        }

        redisState.initMatchState(m.getId());
        logger.info(MATCH_STATE_INITIALIZED, m.getId());

        Map<String, Object> meta = new HashMap<>();
        meta.put("playerCount", req.playerIds().size());
        Long firstPlayer = req.playerIds().isEmpty() ? null : req.playerIds().get(0);
        analyticsEventPublisher.publish("match_started", firstPlayer, (long) m.getId(), meta);

        return new MatchResponse(m.getId(), m.getStatus(), m.getCurrentRound(), req.playerIds(), null);
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
        return new MatchResponse(m.getId(), m.getStatus(), m.getCurrentRound(), players, m.getWinnerId());
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

        List<Long> players = matchPlayers.findAllByMatchId(matchId)
                .stream().map(MatchPlayer::getUserId).toList();
        Map<String, Object> meta = new HashMap<>();
        meta.put("playerCount", players.size());
        Long firstPlayer = players.isEmpty() ? null : players.get(0);
        analyticsEventPublisher.publish("match_started", firstPlayer, (long) matchId, meta);
    }

    @Transactional(readOnly = true)
    public ShopStateResponse getShopState(Integer matchId, Long userId) {
        logger.debug(LOG_GET_SHOP, matchId, userId);
        ensurePlayerInMatch(matchId, userId);
        // Allow reads after FINISHED so clients can refresh UI without 409; mutations still call ensureMatchInProgress.

        PlayerResources pr = resources.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Player resources not found"));
        redisState.initPlayerResourcesIfAbsent(matchId, userId, pr.getGold(), pr.getHp());
        int runtimeGold = redisState.getPlayerGold(matchId, userId, pr.getGold());
        int runtimeHp = redisState.getPlayerHp(matchId, userId, pr.getHp());

        Map<String, Integer> ownedCounts = new LinkedHashMap<>();
        List<String> order = List.of("pawn", "knight", "bishop", "rook", "queen");
        List<BenchSlotResponse> bench = getOrSeedRuntimeBench(matchId, userId);
        for (BenchSlotResponse b : bench) {
            ownedCounts.put(b.piece(), ownedCounts.getOrDefault(b.piece(), 0) + 1);
        }
        List<BoardPieceResponse> boardPiecesFromDb = loadBoardPiecesFromDb(matchId, userId);
        List<BoardPieceResponse> boardPieces = getOrSeedRuntimeBoard(matchId, userId, boardPiecesFromDb);
        for (BoardPieceResponse b : boardPieces) {
            ownedCounts.put(b.piece(), ownedCounts.getOrDefault(b.piece(), 0) + 1);
        }

        final int goldForShop = runtimeGold;
        List<ShopItemResponse> items = order.stream().map(piece -> {
            int cost = resolvePieceCost(piece);
            return new ShopItemResponse(
                    piece,
                    cost,
                    goldForShop >= cost,
                    ownedCounts.getOrDefault(piece, 0)
            );
        }).toList();

        redisState.initPlayerKing(matchId, userId);
        KingSquareResponse king = redisState.getKingSquare(matchId, userId);
        if (king == null) {
            throw new IllegalStateException("Could not load king position");
        }

        long shopEndsAt = redisState.ensureAndGetShopPhaseEndsAtMillis(matchId);

        return new ShopStateResponse(
                runtimeGold,
                runtimeHp,
                PlayerResources.DEFAULT_HP,
                items,
                bench,
                boardPieces,
                king,
                shopEndsAt);
    }

    /**
     * Persists battle round outcome: HP loss for the losing side (≈ rounded pawn eval {@code |cp|/100}), then +2
     * pawns (gold) for White and for Black. Idempotent per match Redis shop-round.
     */
    @Transactional
    public BattleRoundEvaluationResponse finalizeBattleRoundEvaluation(
            Integer matchId,
            int redisRound,
            long currentUserId,
            long whiteUserId,
            long blackUserId,
            String fen,
            int centipawns,
            String advantage,
            List<BoardPieceResponse> whiteBoard,
            List<BoardPieceResponse> blackBoard,
            KingSquareResponse whiteKing,
            KingSquareResponse blackKing,
            List<String> principalVariation
    ) {
        Match matchLocked = matches.findByIdForUpdate(matchId)
                .orElseThrow(() -> new IllegalArgumentException(ERROR_MATCH_NOT_FOUND_MESSAGE + matchId));

        Optional<String> cached = redisState.getCachedBattleEval(matchId, redisRound);
        if (cached.isPresent()) {
            try {
                BattleRoundEvaluationResponse parsed =
                        objectMapper.readValue(cached.get(), BattleRoundEvaluationResponse.class);
                return parsed.forViewer(currentUserId);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Cached battle evaluation is corrupt", e);
            }
        }

        boolean firstApplier = redisState.tryMarkBattleRoundApplied(matchId, redisRound);
        if (!firstApplier) {
            BattleRoundEvaluationResponse existing = awaitCachedBattleEval(matchId, redisRound);
            if (existing != null) {
                return existing.forViewer(currentUserId);
            }
            // Last-resort fallback: avoid double rewards even if cache isn't visible yet.
            // Build a viewer response with current runtime HP snapshot.
            PlayerResources whiteDb = resources.findByMatchIdAndUserId(matchId, whiteUserId)
                    .orElseThrow(() -> new IllegalStateException("Player resources not found"));
            PlayerResources blackDb = resources.findByMatchIdAndUserId(matchId, blackUserId)
                    .orElseThrow(() -> new IllegalStateException("Player resources not found"));
            int whiteHpNow = redisState.getPlayerHp(matchId, whiteUserId, whiteDb.getHp());
            int blackHpNow = redisState.getPlayerHp(matchId, blackUserId, blackDb.getHp());
            Match statusMatch = matches.findById(matchId)
                    .orElseThrow(() -> new IllegalArgumentException(ERROR_MATCH_NOT_FOUND_MESSAGE + matchId));
            boolean matchFinished = statusMatch.isFinished();
            Long winnerUserId = matchFinished ? statusMatch.getWinnerId() : null;
            BattleRoundEvaluationResponse fallback = new BattleRoundEvaluationResponse(
                    fen,
                    centipawns,
                    advantage,
                    whiteUserId,
                    blackUserId,
                    currentUserId == whiteUserId,
                    whiteBoard,
                    blackBoard,
                    whiteKing,
                    blackKing,
                    principalVariation == null ? List.of() : principalVariation,
                    computeBattleViewEndsAtEpochMs(principalVariation == null ? List.of() : principalVariation),
                    whiteHpNow,
                    blackHpNow,
                    matchFinished,
                    winnerUserId
            );
            return fallback.forViewer(currentUserId);
        }

        if (!matchLocked.isInProgress()) {
            throw new IllegalStateException("This match has ended");
        }

        AppliedBattleOutcome applied = applyBattleRoundOutcome(
                matchLocked, matchId, whiteUserId, blackUserId, centipawns);
        persistRuntimeShopStateToDatabase(matchId);

        BattleRoundEvaluationResponse response = new BattleRoundEvaluationResponse(
                fen,
                centipawns,
                advantage,
                whiteUserId,
                blackUserId,
                currentUserId == whiteUserId,
                whiteBoard,
                blackBoard,
                whiteKing,
                blackKing,
                principalVariation == null ? List.of() : principalVariation,
                computeBattleViewEndsAtEpochMs(principalVariation == null ? List.of() : principalVariation),
                applied.hp().whiteHp(),
                applied.hp().blackHp(),
                applied.matchFinished(),
                applied.winnerUserId()
        );

        final String json;
        try {
            json = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize battle evaluation", e);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisState.setCachedBattleEval(matchId, redisRound, json);
                redisState.setLastBattleEval(matchId, redisRound, json);
                long shopEndsAtEpochMs =
                        response.battleViewEndsAt()
                                + Duration.ofSeconds(GameStateRedisService.SHOP_PHASE_DURATION_SECONDS)
                                        .toMillis();
                redisState.incrementShopRoundAfterBattle(matchId, shopEndsAtEpochMs);
                if (applied.matchFinished()
                        && applied.winnerUserId() != null
                        && applied.loserUserId() != null) {
                    matchRatingNotifier.notifyMatchFinished(applied.winnerUserId(), applied.loserUserId());
                }
            }
        });

        return response;
    }

    private BattleRoundEvaluationResponse awaitCachedBattleEval(Integer matchId, int redisRound) {
        long deadline = System.currentTimeMillis() + 1500L;
        while (System.currentTimeMillis() < deadline) {
            Optional<String> cached = redisState.getCachedBattleEval(matchId, redisRound);
            if (cached.isPresent()) {
                try {
                    return objectMapper.readValue(cached.get(), BattleRoundEvaluationResponse.class);
                } catch (JsonProcessingException ignored) {
                    return null;
                }
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private record AppliedBattleOutcome(
            BattleRoundHpSnapshot hp,
            boolean matchFinished,
            Long winnerUserId,
            Long loserUserId
    ) {}

    private AppliedBattleOutcome applyBattleRoundOutcome(
            Match matchRow,
            Integer matchId,
            long whiteUserId,
            long blackUserId,
            int centipawns
    ) {
        PlayerResources whiteDb = resources.findByMatchIdAndUserId(matchId, whiteUserId)
                .orElseThrow(() -> new IllegalStateException("Player resources not found"));
        PlayerResources blackDb = resources.findByMatchIdAndUserId(matchId, blackUserId)
                .orElseThrow(() -> new IllegalStateException("Player resources not found"));
        redisState.initPlayerResourcesIfAbsent(matchId, whiteUserId, whiteDb.getGold(), whiteDb.getHp());
        redisState.initPlayerResourcesIfAbsent(matchId, blackUserId, blackDb.getGold(), blackDb.getHp());

        int whiteHp = redisState.getPlayerHp(matchId, whiteUserId, whiteDb.getHp());
        int blackHp = redisState.getPlayerHp(matchId, blackUserId, blackDb.getHp());
        int whiteGold = redisState.getPlayerGold(matchId, whiteUserId, whiteDb.getGold());
        int blackGold = redisState.getPlayerGold(matchId, blackUserId, blackDb.getGold());

        long loserId = 0L;
        long winnerId = 0L;
        boolean justEliminated = false;
        if (centipawns != 0) {
            loserId = centipawns > 0 ? blackUserId : whiteUserId;
            winnerId = loserId == whiteUserId ? blackUserId : whiteUserId;
            int loserHpBefore = loserId == whiteUserId ? whiteHp : blackHp;
            int damage = (int) Math.round(Math.abs(centipawns) / 100.0);
            damage = Math.min(Math.max(0, damage), BATTLE_HP_MAX_DAMAGE_PAWNS_ROUNDED);
            int loserHpAfter = Math.max(0, loserHpBefore - damage);
            if (loserId == whiteUserId) {
                whiteHp = loserHpAfter;
            } else {
                blackHp = loserHpAfter;
            }
            justEliminated = loserHpBefore > 0 && loserHpAfter == 0;
            logger.info(LOG_BATTLE_HP, matchId, centipawns, loserId, damage);
        }

        whiteGold += BATTLE_ROUND_PAWNS_PER_SIDE;
        blackGold += BATTLE_ROUND_PAWNS_PER_SIDE;
        redisState.setPlayerGold(matchId, whiteUserId, whiteGold);
        redisState.setPlayerGold(matchId, blackUserId, blackGold);
        redisState.setPlayerHp(matchId, whiteUserId, whiteHp);
        redisState.setPlayerHp(matchId, blackUserId, blackHp);
        logger.debug(LOG_BATTLE_PAWNS, matchId, whiteUserId, BATTLE_ROUND_PAWNS_PER_SIDE);
        logger.debug(LOG_BATTLE_PAWNS, matchId, blackUserId, BATTLE_ROUND_PAWNS_PER_SIDE);

        Map<String, Object> battleMeta = new HashMap<>();
        battleMeta.put("centipawns", centipawns);
        battleMeta.put("whiteHp", whiteHp);
        battleMeta.put("blackHp", blackHp);
        analyticsEventPublisher.publish("battle_round", whiteUserId, (long) matchId, battleMeta);

        if (justEliminated) {
            matchRow.finish(winnerId);
            matches.save(matchRow);
            Map<String, Object> finishMeta = new HashMap<>();
            finishMeta.put("winnerUserId", winnerId);
            finishMeta.put("loserUserId", loserId);
            analyticsEventPublisher.publish("match_finished", winnerId, (long) matchId, finishMeta);
        }

        return new AppliedBattleOutcome(
                new BattleRoundHpSnapshot(whiteHp, blackHp),
                justEliminated,
                justEliminated ? winnerId : null,
                justEliminated ? loserId : null
        );
    }

    private long computeBattleViewEndsAtEpochMs(List<String> principalVariation) {
        int plies = Math.min(BATTLE_VIEW_MAX_PLIES, principalVariation.size());
        long replayMs = BATTLE_VIEW_BASE_DELAY_MS + (plies * BATTLE_VIEW_STEP_MS) + BATTLE_VIEW_END_PAUSE_MS;
        return System.currentTimeMillis() + replayMs + BATTLE_VIEW_SAFETY_BUFFER_MS;
    }

    @Transactional
    public BuyPieceResponse buyPiece(Integer matchId, Long userId, BuyPieceRequest req) {
        String pieceKey = normalizePieceKey(req.piece());
        logger.info(LOG_BUY_PIECE, matchId, userId, pieceKey);

        ensurePlayerInMatch(matchId, userId);
        ensureMatchInProgress(matchId);
        Piece piece = findPieceEntity(pieceKey);
        PlayerResources pr = resources.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Player resources not found"));
        redisState.initPlayerResourcesIfAbsent(matchId, userId, pr.getGold(), pr.getHp());

        int cost = piece.getCostGold();
        int moneyBefore = redisState.getPlayerGold(matchId, userId, pr.getGold());
        if (moneyBefore < cost) {
            throw new IllegalStateException("Not enough pawns to buy this piece");
        }

        int slot = req.slot() != null ? req.slot() : findFirstFreeBenchSlot(matchId, userId);
        if (redisState.isBenchSlotOccupied(matchId, userId, slot)) {
            throw new IllegalStateException("Bench slot is already occupied");
        }

        int moneyAfter = moneyBefore - cost;
        redisState.setPlayerGold(matchId, userId, moneyAfter);
        redisState.setBenchSlot(matchId, userId, slot, pieceKey);

        Map<String, Object> meta = new HashMap<>();
        meta.put("piece", pieceKey);
        meta.put("cost", cost);
        analyticsEventPublisher.publish("piece_purchased", userId, (long) matchId, meta);

        return new BuyPieceResponse(pieceKey, moneyBefore, moneyAfter, slot);
    }

    @Transactional
    public void placePieceFromBench(Integer matchId, Long userId, PlacePieceRequest req) {
        logger.info(LOG_PLACE_PIECE, matchId, userId, req.benchSlot(), req.squareX(), req.squareY());

        ensurePlayerInMatch(matchId, userId);
        ensureMatchInProgress(matchId);

        String benchPieceKey = redisState.getBenchSlotPieceKey(matchId, userId, req.benchSlot());
        if (benchPieceKey == null || benchPieceKey.isBlank()) {
            throw new IllegalStateException("That bench slot is empty");
        }
        if ("pawn".equals(benchPieceKey)) {
            validatePawnSquare(req.squareX(), req.squareY());
        }

        ensureTargetNotOnKing(matchId, userId, req.squareX(), req.squareY());

        if (redisState.isPlayerBoardSquareOccupied(matchId, userId, req.squareX(), req.squareY())) {
            throw new IllegalStateException("That square is already occupied");
        }

        redisState.clearBenchSlot(matchId, userId, req.benchSlot());
        redisState.setPlayerBoardSquare(matchId, userId, req.squareX(), req.squareY(), benchPieceKey);
    }

    @Transactional
    public SellPieceResponse sellPiece(Integer matchId, Long userId, SellPieceRequest req) {
        ensurePlayerInMatch(matchId, userId);
        ensureMatchInProgress(matchId);

        if (req.benchSlot() != null) {
            if (req.fromX() != null || req.fromY() != null) {
                throw new IllegalArgumentException("Use either benchSlot or board coordinates, not both");
            }
            return sellFromBench(matchId, userId, req.benchSlot());
        }
        if (req.fromX() != null && req.fromY() != null) {
            return sellFromBoard(matchId, userId, req.fromX(), req.fromY());
        }
        throw new IllegalArgumentException("Specify benchSlot or both fromX and fromY");
    }

    private SellPieceResponse sellFromBench(Integer matchId, Long userId, int benchSlot) {
        if (benchSlot < 0 || benchSlot > 7) {
            throw new IllegalArgumentException("Invalid bench slot");
        }
        String pieceKey = redisState.getBenchSlotPieceKey(matchId, userId, benchSlot);
        if (pieceKey == null || pieceKey.isBlank()) {
            throw new IllegalStateException("That bench slot is empty");
        }
        return finalizeSell(matchId, userId, pieceKey, false, benchSlot, null, null);
    }

    private SellPieceResponse sellFromBoard(Integer matchId, Long userId, int fromX, int fromY) {
        if (fromX < 0 || fromX > 7 || fromY < 0 || fromY > 7) {
            throw new IllegalArgumentException("Square out of bounds");
        }
        redisState.initPlayerKing(matchId, userId);
        KingSquareResponse king = redisState.getKingSquare(matchId, userId);
        if (king != null && king.x() == fromX && king.y() == fromY) {
            throw new IllegalStateException("You cannot sell your king");
        }
        String pieceKey = redisState.getPlayerBoardPieceKey(matchId, userId, fromX, fromY);
        if (pieceKey == null || pieceKey.isBlank()) {
            throw new IllegalStateException("That square does not hold a board piece");
        }
        return finalizeSell(matchId, userId, pieceKey, true, null, fromX, fromY);
    }

    private SellPieceResponse finalizeSell(
            Integer matchId,
            Long userId,
            String pieceKey,
            boolean fromBoard,
            Integer benchSlot,
            Integer boardX,
            Integer boardY
    ) {
        int refund = resolvePieceCost(pieceKey);
        PlayerResources pr = resources.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Player resources not found"));
        redisState.initPlayerResourcesIfAbsent(matchId, userId, pr.getGold(), pr.getHp());
        int moneyBefore = redisState.getPlayerGold(matchId, userId, pr.getGold());
        int moneyAfter = moneyBefore + refund;
        redisState.setPlayerGold(matchId, userId, moneyAfter);
        if (fromBoard && boardX != null && boardY != null) {
            redisState.clearPlayerBoardSquare(matchId, userId, boardX, boardY);
        } else if (benchSlot != null) {
            redisState.clearBenchSlot(matchId, userId, benchSlot);
        }
        logger.info(LOG_SELL_PIECE, matchId, userId, pieceKey, refund);
        return new SellPieceResponse(pieceKey, moneyBefore, moneyAfter);
    }

    @Transactional
    public void moveBoardPiece(Integer matchId, Long userId, MovePieceRequest req) {
        logger.info(LOG_MOVE_PIECE, matchId, userId, req.fromX(), req.fromY(), req.toX(), req.toY());

        ensurePlayerInMatch(matchId, userId);
        ensureMatchInProgress(matchId);

        if (req.fromX().equals(req.toX()) && req.fromY().equals(req.toY())) {
            return;
        }

        String pieceKey = redisState.getPlayerBoardPieceKey(matchId, userId, req.fromX(), req.fromY());
        if (pieceKey == null || pieceKey.isBlank()) {
            throw new IllegalArgumentException("No piece on that square");
        }
        if ("pawn".equals(pieceKey)) {
            validatePawnSquare(req.toX(), req.toY());
        }

        ensureTargetNotOnKing(matchId, userId, req.toX(), req.toY());

        if (redisState.isPlayerBoardSquareOccupied(matchId, userId, req.toX(), req.toY())) {
            throw new IllegalStateException("That square is already occupied");
        }

        redisState.clearPlayerBoardSquare(matchId, userId, req.fromX(), req.fromY());
        redisState.setPlayerBoardSquare(matchId, userId, req.toX(), req.toY(), pieceKey);
    }

    @Transactional
    public void moveKing(Integer matchId, Long userId, MoveKingRequest req) {
        ensurePlayerInMatch(matchId, userId);
        ensureMatchInProgress(matchId);
        validateKingLane(req.toX());
        validateKingRank(req.toY());

        redisState.initPlayerKing(matchId, userId);
        KingSquareResponse current = redisState.getKingSquare(matchId, userId);
        if (current == null) {
            throw new IllegalStateException("King position not available");
        }
        if (current.x() == req.toX() && current.y() == req.toY()) {
            return;
        }

        ensureTargetNotOccupiedByPiece(matchId, userId, req.toX(), req.toY());

        redisState.setKingSquare(matchId, userId, req.toX(), req.toY());
    }

    @Transactional
    public void resignMatch(Integer matchId, Long userId) {
        ensurePlayerInMatch(matchId, userId);
        List<Long> ordered = matchPlayers.findAllByMatchId(matchId).stream()
                .map(MatchPlayer::getUserId)
                .sorted()
                .toList();
        if (ordered.size() != 2) {
            throw new IllegalStateException("Resign requires exactly two players");
        }
        long opponent = ordered.get(0).equals(userId) ? ordered.get(1) : ordered.get(0);
        Match m = matches.findByIdForUpdate(matchId)
                .orElseThrow(() -> new IllegalArgumentException(ERROR_MATCH_NOT_FOUND_MESSAGE + matchId));
        if (!m.isInProgress()) {
            throw new IllegalStateException("This match has ended");
        }
        m.finish(opponent);
        matches.save(m);
        persistRuntimeShopStateToDatabase(matchId);
        Map<String, Object> meta = new HashMap<>();
        meta.put("winnerUserId", opponent);
        meta.put("loserUserId", userId);
        meta.put("reason", "resign");
        analyticsEventPublisher.publish("match_finished", opponent, (long) matchId, meta);
        long win = opponent;
        long lose = userId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                matchRatingNotifier.notifyMatchFinished(win, lose);
            }
        });
    }

    public void ensureMatchInProgress(Integer matchId) {
        Match match = matches.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException(ERROR_MATCH_NOT_FOUND_MESSAGE + matchId));
        if (match.isFinished()) {
            throw new IllegalStateException("This match has ended");
        }
        if (!match.isInProgress()) {
            // Defensive: createMatch now starts the match immediately, so WAITING should not occur in practice.
            throw new IllegalStateException("This match has not started yet");
        }
    }

    private void validateKingLane(int col) {
        if (col < KING_LANE_MIN_COL || col > KING_LANE_MAX_COL) {
            throw new IllegalStateException("Your king move is out of board bounds");
        }
    }

    private void validateKingRank(int row) {
        if (row < KING_RANK_MIN_ROW || row > KING_RANK_MAX_ROW) {
            throw new IllegalStateException("Your king may only be on ranks 1–4");
        }
    }

    private void validatePawnSquare(int file, int row) {
        if (row < PAWN_RANK_MIN_ROW || row > PAWN_RANK_MAX_ROW) {
            throw new IllegalStateException("Pawns may only be on ranks 2–4");
        }
    }

    private boolean isPawnPiece(Piece piece) {
        return "pawn".equals(normalizePieceKey(piece.getName()));
    }

    private void ensureTargetNotOnKing(Integer matchId, Long userId, int x, int y) {
        redisState.initPlayerKing(matchId, userId);
        KingSquareResponse king = redisState.getKingSquare(matchId, userId);
        if (king != null && king.x() == x && king.y() == y) {
            throw new IllegalStateException("That square is occupied by your king");
        }
    }

    /** King tile may stack visually only on empty squares for pieces. */
    private void ensureTargetNotOccupiedByPiece(Integer matchId, Long userId, int x, int y) {
        if (redisState.isPlayerBoardSquareOccupied(matchId, userId, x, y)) {
            throw new IllegalStateException("That square is already occupied");
        }
    }

    private List<BoardPieceResponse> getOrSeedRuntimeBoard(
            Integer matchId,
            Long userId,
            List<BoardPieceResponse> boardPiecesFromDb
    ) {
        if (!redisState.hasAnyPlayerBoardData(matchId, userId)) {
            /*
             Empty hash either means first load (seed from persistence) or the player legitimately cleared the
             board in Redis via sell/move—DB inventory is only snapshotted after battles, so re-seeding here would
             resurrect sold pieces ("infinite sell").
             */
            if (redisState.isRuntimeShopBoardTouched(matchId, userId)) {
                return List.of();
            }
            List<GameStateRedisService.RuntimeBoardPiece> seed = boardPiecesFromDb.stream()
                    .map(p -> new GameStateRedisService.RuntimeBoardPiece(p.x(), p.y(), p.piece()))
                    .toList();
            redisState.replacePlayerBoard(matchId, userId, seed);
            redisState.markRuntimeShopBoardTouched(matchId, userId);
            return boardPiecesFromDb;
        }
        List<GameStateRedisService.RuntimeBoardPiece> fromRedis = redisState.getPlayerBoard(matchId, userId);
        redisState.markRuntimeShopBoardTouched(matchId, userId);
        return fromRedis.stream()
                .map(p -> new BoardPieceResponse(p.x(), p.y(), p.pieceKey()))
                .toList();
    }

    private void ensurePlayerInMatch(Integer matchId, Long userId) {
        boolean inMatch = matchPlayers.existsByMatchIdAndUserId(matchId, userId);
        if (!inMatch) {
            throw new IllegalArgumentException("Player is not part of this match");
        }
    }

    private Piece findPieceEntity(String pieceKey) {
        return pieces.findByNameIgnoreCase(toDbPieceName(pieceKey))
                .orElseThrow(() -> new IllegalArgumentException("Unknown piece: " + pieceKey));
    }

    private int resolvePieceCost(String pieceKey) {
        return switch (pieceKey) {
            case "pawn" -> 1;
            case "knight", "bishop" -> 3;
            case "rook" -> 5;
            case "queen" -> 8;
            default -> 99;
        };
    }

    private String toDbPieceName(String pieceKey) {
        return switch (pieceKey) {
            case "pawn" -> "Pawn";
            case "knight" -> "Knight";
            case "bishop" -> "Bishop";
            case "rook" -> "Rook";
            case "queen" -> "Queen";
            default -> pieceKey;
        };
    }

    private String normalizePieceKey(String piece) {
        return piece == null ? "" : piece.trim().toLowerCase();
    }

    private int findFirstFreeBenchSlot(Integer matchId, Long userId) {
        for (int slot = 0; slot < 8; slot++) {
            boolean occupied = redisState.isBenchSlotOccupied(matchId, userId, slot);
            if (!occupied) return slot;
        }
        throw new IllegalStateException("Bench is full");
    }

    private List<BenchSlotResponse> getOrSeedRuntimeBench(Integer matchId, Long userId) {
        if (!redisState.hasAnyBenchData(matchId, userId)) {
            if (redisState.isRuntimeShopBenchTouched(matchId, userId)) {
                return List.of();
            }
            List<BenchSlotResponse> benchFromDb = loadBenchFromDb(matchId, userId);
            List<GameStateRedisService.RuntimeBenchPiece> seed = benchFromDb.stream()
                    .map(b -> new GameStateRedisService.RuntimeBenchPiece(b.slot(), b.piece()))
                    .toList();
            redisState.replaceBench(matchId, userId, seed);
            redisState.markRuntimeShopBenchTouched(matchId, userId);
            return benchFromDb;
        }
        List<BenchSlotResponse> bench = redisState.getBench(matchId, userId).stream()
                .map(b -> new BenchSlotResponse(b.slot(), b.pieceKey()))
                .sorted(Comparator.comparingInt(BenchSlotResponse::slot))
                .toList();
        redisState.markRuntimeShopBenchTouched(matchId, userId);
        return bench;
    }

    private List<BenchSlotResponse> loadBenchFromDb(Integer matchId, Long userId) {
        List<BenchSlotResponse> out = new ArrayList<>();
        for (PlayerInventory item : inventory.findAllByMatchIdAndUserId(matchId, userId)) {
            if (item.isOnBoard() || item.getPositionY() != BENCH_POSITION_Y) continue;
            pieces.findById(item.getPieceId()).ifPresent(pieceEntity ->
                    out.add(new BenchSlotResponse(item.getPositionX(), normalizePieceKey(pieceEntity.getName()))));
        }
        out.sort(Comparator.comparingInt(BenchSlotResponse::slot));
        return out;
    }

    private List<BoardPieceResponse> loadBoardPiecesFromDb(Integer matchId, Long userId) {
        List<BoardPieceResponse> out = new ArrayList<>();
        for (PlayerInventory item : inventory.findAllByMatchIdAndUserId(matchId, userId)) {
            if (!item.isOnBoard()) continue;
            pieces.findById(item.getPieceId()).ifPresent(pieceEntity ->
                    out.add(new BoardPieceResponse(item.getPositionX(), item.getPositionY(), normalizePieceKey(pieceEntity.getName()))));
        }
        return out;
    }

    private void persistRuntimeShopStateToDatabase(Integer matchId) {
        Map<String, Piece> pieceByKey = new HashMap<>();
        for (String key : List.of("pawn", "knight", "bishop", "rook", "queen")) {
            pieceByKey.put(key, findPieceEntity(key));
        }

        for (MatchPlayer mp : matchPlayers.findAllByMatchId(matchId)) {
            long userId = mp.getUserId();
            PlayerResources pr = resources.findByMatchIdAndUserId(matchId, userId).orElseGet(() -> {
                PlayerResources created = new PlayerResources();
                created.setMatchId(matchId);
                created.setUserId(userId);
                created.setGold(PlayerResources.DEFAULT_GOLD);
                created.setHp(PlayerResources.DEFAULT_HP);
                created.setLevel(PlayerResources.DEFAULT_LEVEL);
                created.setExperience(PlayerResources.DEFAULT_EXPERIENCE);
                return created;
            });
            redisState.initPlayerResourcesIfAbsent(matchId, userId, pr.getGold(), pr.getHp());
            pr.setGold(redisState.getPlayerGold(matchId, userId, pr.getGold()));
            pr.setHp(redisState.getPlayerHp(matchId, userId, pr.getHp()));
            resources.save(pr);

            inventory.deleteAllByMatchIdAndUserId(matchId, userId);
            // Ensure DELETE reaches DB before INSERTs in this transaction,
            // otherwise unique (match_id,user_id,position_x,position_y) can collide.
            inventory.flush();

            for (GameStateRedisService.RuntimeBenchPiece b : redisState.getBench(matchId, userId)) {
                Piece piece = pieceByKey.get(normalizePieceKey(b.pieceKey()));
                if (piece == null) continue;
                PlayerInventory item = new PlayerInventory();
                item.setMatchId(matchId);
                item.setUserId(userId);
                item.setPieceId(piece.getId());
                item.setPositionX(b.slot());
                item.setPositionY(BENCH_POSITION_Y);
                item.setOnBoard(false);
                inventory.save(item);
            }

            for (GameStateRedisService.RuntimeBoardPiece b : redisState.getPlayerBoard(matchId, userId)) {
                Piece piece = pieceByKey.get(normalizePieceKey(b.pieceKey()));
                if (piece == null) continue;
                PlayerInventory item = new PlayerInventory();
                item.setMatchId(matchId);
                item.setUserId(userId);
                item.setPieceId(piece.getId());
                item.setPositionX(b.x());
                item.setPositionY(b.y());
                item.setOnBoard(true);
                inventory.save(item);
            }
        }
    }
}