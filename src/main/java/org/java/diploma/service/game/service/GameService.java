package org.java.diploma.service.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.Comparator;
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
     * Max HP removed per round after converting eval to pawns ({@code round(|cp|/100)}). Prevents one-shot wipes
     * from extreme engine scores while matching the eval bar scale (e.g. 5.7 pawns → ~6 HP, not 10).
     */
    private static final int BATTLE_HP_MAX_DAMAGE_PAWNS_ROUNDED = 25;
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

    public GameService(MatchRepository matches,
                       MatchPlayerRepository matchPlayers,
                       PlayerResourcesRepository resources,
                       PieceRepository pieces,
                       PlayerInventoryRepository inventory,
                       GameStateRedisService redisState,
                       ObjectMapper objectMapper) {
        this.matches = matches;
        this.matchPlayers = matchPlayers;
        this.resources = resources;
        this.pieces = pieces;
        this.inventory = inventory;
        this.redisState = redisState;
        this.objectMapper = objectMapper;
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
            // Start with 2 so players can buy a pawn immediately.
            pr.setGold(2);
            pr.setHp(PlayerResources.DEFAULT_HP);
            pr.setLevel(PlayerResources.DEFAULT_LEVEL);
            pr.setExperience(PlayerResources.DEFAULT_EXPERIENCE);
            resources.save(pr);
            logger.debug(PLAYER_RESOURCES_INITIALIZED, m.getId(), userId);

            redisState.initPlayerKing(m.getId(), userId);
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

    @Transactional(readOnly = true)
    public ShopStateResponse getShopState(Integer matchId, Long userId) {
        logger.debug(LOG_GET_SHOP, matchId, userId);
        ensurePlayerInMatch(matchId, userId);

        PlayerResources pr = resources.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Player resources not found"));

        Map<String, Integer> ownedCounts = new LinkedHashMap<>();
        for (PlayerInventory item : inventory.findAllByMatchIdAndUserId(matchId, userId)) {
            Optional<Piece> p = pieces.findById(item.getPieceId());
            p.ifPresent(piece -> {
                String key = normalizePieceKey(piece.getName());
                ownedCounts.put(key, ownedCounts.getOrDefault(key, 0) + 1);
            });
        }

        List<String> order = List.of("pawn", "knight", "bishop", "rook", "queen");
        List<ShopItemResponse> items = order.stream().map(piece -> {
            int cost = resolvePieceCost(piece);
            return new ShopItemResponse(
                    piece,
                    cost,
                    pr.getGold() >= cost,
                    ownedCounts.getOrDefault(piece, 0)
            );
        }).toList();

        List<BenchSlotResponse> bench = new ArrayList<>();
        List<BoardPieceResponse> boardPieces = new ArrayList<>();
        for (PlayerInventory item : inventory.findAllByMatchIdAndUserId(matchId, userId)) {
            Optional<Piece> pieceOpt = pieces.findById(item.getPieceId());
            if (item.isOnBoard()) {
                pieceOpt.ifPresent(pieceEntity -> boardPieces.add(new BoardPieceResponse(
                        item.getPositionX(),
                        item.getPositionY(),
                        normalizePieceKey(pieceEntity.getName()))));
                continue;
            }
            if (item.getPositionY() != BENCH_POSITION_Y) {
                continue;
            }
            pieceOpt.ifPresent(pieceEntity -> bench.add(new BenchSlotResponse(
                    item.getPositionX(),
                    normalizePieceKey(pieceEntity.getName()))));
        }
        bench.sort(Comparator.comparingInt(BenchSlotResponse::slot));

        redisState.initPlayerKing(matchId, userId);
        KingSquareResponse king = redisState.getKingSquare(matchId, userId);
        if (king == null) {
            throw new IllegalStateException("Could not load king position");
        }

        long shopEndsAt = redisState.ensureAndGetShopPhaseEndsAtMillis(matchId);

        return new ShopStateResponse(
                pr.getGold(),
                pr.getHp(),
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
        matches.findByIdForUpdate(matchId)
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

        BattleRoundHpSnapshot hp = applyBattleRoundOutcome(matchId, whiteUserId, blackUserId, centipawns);

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
                hp.whiteHp(),
                hp.blackHp()
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
                redisState.incrementShopRoundAfterBattle(matchId);
            }
        });

        return response;
    }

    private BattleRoundHpSnapshot applyBattleRoundOutcome(
            Integer matchId,
            long whiteUserId,
            long blackUserId,
            int centipawns
    ) {
        if (centipawns != 0) {
            long loserId = centipawns > 0 ? blackUserId : whiteUserId;
            int damage = (int) Math.round(Math.abs(centipawns) / 100.0);
            damage = Math.min(Math.max(0, damage), BATTLE_HP_MAX_DAMAGE_PAWNS_ROUNDED);
            PlayerResources loser = resources.findByMatchIdAndUserId(matchId, loserId)
                    .orElseThrow(() -> new IllegalStateException("Player resources not found for loser"));
            int before = loser.getHp();
            loser.setHp(Math.max(0, before - damage));
            resources.save(loser);
            logger.info(LOG_BATTLE_HP, matchId, centipawns, loserId, damage);
        }

        for (long userId : List.of(whiteUserId, blackUserId)) {
            PlayerResources pr = resources.findByMatchIdAndUserId(matchId, userId)
                    .orElseThrow(() -> new IllegalStateException("Player resources not found"));
            pr.setGold(pr.getGold() + BATTLE_ROUND_PAWNS_PER_SIDE);
            resources.save(pr);
            logger.debug(LOG_BATTLE_PAWNS, matchId, userId, BATTLE_ROUND_PAWNS_PER_SIDE);
        }

        int w = resources.findByMatchIdAndUserId(matchId, whiteUserId)
                .map(PlayerResources::getHp)
                .orElse(PlayerResources.DEFAULT_HP);
        int b = resources.findByMatchIdAndUserId(matchId, blackUserId)
                .map(PlayerResources::getHp)
                .orElse(PlayerResources.DEFAULT_HP);
        return new BattleRoundHpSnapshot(w, b);
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
        Piece piece = findPieceEntity(pieceKey);
        PlayerResources pr = resources.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Player resources not found"));

        int cost = piece.getCostGold();
        int moneyBefore = pr.getGold();
        if (moneyBefore < cost) {
            throw new IllegalStateException("Not enough pawns to buy this piece");
        }

        int slot = req.slot() != null ? req.slot() : findFirstFreeBenchSlot(matchId, userId);
        if (inventory.existsByMatchIdAndUserIdAndPositionXAndPositionY(matchId, userId, slot, BENCH_POSITION_Y)) {
            throw new IllegalStateException("Bench slot is already occupied");
        }

        pr.setGold(moneyBefore - cost);
        resources.save(pr);

        PlayerInventory item = new PlayerInventory();
        item.setMatchId(matchId);
        item.setUserId(userId);
        item.setPieceId(piece.getId());
        item.setPositionX(slot);
        item.setPositionY(BENCH_POSITION_Y);
        item.setOnBoard(false);
        inventory.save(item);

        return new BuyPieceResponse(pieceKey, moneyBefore, pr.getGold(), slot);
    }

    @Transactional
    public void placePieceFromBench(Integer matchId, Long userId, PlacePieceRequest req) {
        logger.info(LOG_PLACE_PIECE, matchId, userId, req.benchSlot(), req.squareX(), req.squareY());

        ensurePlayerInMatch(matchId, userId);

        PlayerInventory benchItem = inventory
                .findByMatchIdAndUserIdAndPositionXAndPositionY(matchId, userId, req.benchSlot(), BENCH_POSITION_Y)
                .orElseThrow(() -> new IllegalArgumentException("No piece in that bench slot"));

        if (benchItem.isOnBoard()) {
            throw new IllegalStateException("That bench slot is empty");
        }

        Piece benchPiece = pieces.findById(benchItem.getPieceId())
                .orElseThrow(() -> new IllegalStateException("Unknown piece"));
        if (isPawnPiece(benchPiece)) {
            validatePawnSquare(req.squareX(), req.squareY());
        }

        ensureTargetNotOnKing(matchId, userId, req.squareX(), req.squareY());

        if (inventory.existsByMatchIdAndUserIdAndPositionXAndPositionYAndIsOnBoardIsTrue(
                matchId, userId, req.squareX(), req.squareY())) {
            throw new IllegalStateException("That square is already occupied");
        }

        benchItem.setPositionX(req.squareX());
        benchItem.setPositionY(req.squareY());
        benchItem.setOnBoard(true);
        inventory.save(benchItem);
    }

    @Transactional
    public SellPieceResponse sellPiece(Integer matchId, Long userId, SellPieceRequest req) {
        ensurePlayerInMatch(matchId, userId);

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
        PlayerInventory item = inventory
                .findByMatchIdAndUserIdAndPositionXAndPositionY(matchId, userId, benchSlot, BENCH_POSITION_Y)
                .orElseThrow(() -> new IllegalArgumentException("No piece in that bench slot"));
        if (item.isOnBoard()) {
            throw new IllegalStateException("That bench slot is empty");
        }
        Piece pieceEntity = pieces.findById(item.getPieceId())
                .orElseThrow(() -> new IllegalStateException("Unknown piece"));
        return finalizeSell(matchId, userId, item, pieceEntity);
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
        PlayerInventory item = inventory
                .findByMatchIdAndUserIdAndPositionXAndPositionY(matchId, userId, fromX, fromY)
                .orElseThrow(() -> new IllegalArgumentException("No piece on that square"));
        if (!item.isOnBoard()) {
            throw new IllegalStateException("That square does not hold a board piece");
        }
        Piece pieceEntity = pieces.findById(item.getPieceId())
                .orElseThrow(() -> new IllegalStateException("Unknown piece"));
        return finalizeSell(matchId, userId, item, pieceEntity);
    }

    private SellPieceResponse finalizeSell(Integer matchId, Long userId, PlayerInventory item, Piece pieceEntity) {
        String pieceKey = normalizePieceKey(pieceEntity.getName());
        int refund = pieceEntity.getCostGold();
        PlayerResources pr = resources.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Player resources not found"));
        int moneyBefore = pr.getGold();
        pr.setGold(moneyBefore + refund);
        resources.save(pr);
        inventory.delete(item);
        logger.info(LOG_SELL_PIECE, matchId, userId, pieceKey, refund);
        return new SellPieceResponse(pieceKey, moneyBefore, pr.getGold());
    }

    @Transactional
    public void moveBoardPiece(Integer matchId, Long userId, MovePieceRequest req) {
        logger.info(LOG_MOVE_PIECE, matchId, userId, req.fromX(), req.fromY(), req.toX(), req.toY());

        ensurePlayerInMatch(matchId, userId);

        if (req.fromX().equals(req.toX()) && req.fromY().equals(req.toY())) {
            return;
        }

        PlayerInventory piece = inventory
                .findByMatchIdAndUserIdAndPositionXAndPositionY(matchId, userId, req.fromX(), req.fromY())
                .orElseThrow(() -> new IllegalArgumentException("No piece on that square"));

        if (!piece.isOnBoard()) {
            throw new IllegalArgumentException("That square does not hold a board piece");
        }

        Piece pieceEntity = pieces.findById(piece.getPieceId())
                .orElseThrow(() -> new IllegalStateException("Unknown piece"));
        if (isPawnPiece(pieceEntity)) {
            validatePawnSquare(req.toX(), req.toY());
        }

        ensureTargetNotOnKing(matchId, userId, req.toX(), req.toY());

        if (inventory.existsByMatchIdAndUserIdAndPositionXAndPositionYAndIsOnBoardIsTrue(
                matchId, userId, req.toX(), req.toY())) {
            throw new IllegalStateException("That square is already occupied");
        }

        piece.setPositionX(req.toX());
        piece.setPositionY(req.toY());
        inventory.save(piece);
    }

    @Transactional
    public void moveKing(Integer matchId, Long userId, MoveKingRequest req) {
        ensurePlayerInMatch(matchId, userId);
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
        if (inventory.existsByMatchIdAndUserIdAndPositionXAndPositionYAndIsOnBoardIsTrue(
                matchId, userId, x, y)) {
            throw new IllegalStateException("That square is already occupied");
        }
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
        return pieces.findByNameIgnoreCase(toDbPieceName(pieceKey))
                .map(Piece::getCostGold)
                .orElseGet(() -> switch (pieceKey) {
                    case "pawn" -> 1;
                    case "knight", "bishop" -> 3;
                    case "rook" -> 5;
                    case "queen" -> 8;
                    default -> 99;
                });
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
            boolean occupied = inventory.existsByMatchIdAndUserIdAndPositionXAndPositionY(
                    matchId, userId, slot, BENCH_POSITION_Y);
            if (!occupied) return slot;
        }
        throw new IllegalStateException("Bench is full");
    }
}