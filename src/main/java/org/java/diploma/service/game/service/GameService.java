package org.java.diploma.service.game.service;

import org.java.diploma.service.game.dto.BenchSlotResponse;
import org.java.diploma.service.game.dto.BoardPieceResponse;
import org.java.diploma.service.game.dto.CreateMatchRequest;
import org.java.diploma.service.game.dto.BuyPieceRequest;
import org.java.diploma.service.game.dto.BuyPieceResponse;
import org.java.diploma.service.game.dto.MatchResponse;
import org.java.diploma.service.game.dto.MovePieceRequest;
import org.java.diploma.service.game.dto.PlacePieceRequest;
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

    /** Bench rows use this Y so they never collide with board squares (0–7). */
    private static final int BENCH_POSITION_Y = 8;
    private static final int WHITE_KING_HOME_COL = 4;
    private static final int WHITE_KING_HOME_ROW = 7;

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
    private final PieceRepository pieces;
    private final PlayerInventoryRepository inventory;
    private final GameStateRedisService redisState;

    public GameService(MatchRepository matches,
                       MatchPlayerRepository matchPlayers,
                       PlayerResourcesRepository resources,
                       PieceRepository pieces,
                       PlayerInventoryRepository inventory,
                       GameStateRedisService redisState) {
        this.matches = matches;
        this.matchPlayers = matchPlayers;
        this.resources = resources;
        this.pieces = pieces;
        this.inventory = inventory;
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
            // Start with 2 so players can buy a pawn immediately.
            pr.setGold(2);
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

        return new ShopStateResponse(pr.getGold(), items, bench, boardPieces);
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

        if (req.squareX() == WHITE_KING_HOME_COL && req.squareY() == WHITE_KING_HOME_ROW) {
            throw new IllegalStateException("That square is reserved for your king");
        }

        PlayerInventory benchItem = inventory
                .findByMatchIdAndUserIdAndPositionXAndPositionY(matchId, userId, req.benchSlot(), BENCH_POSITION_Y)
                .orElseThrow(() -> new IllegalArgumentException("No piece in that bench slot"));

        if (benchItem.isOnBoard()) {
            throw new IllegalStateException("That bench slot is empty");
        }

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
    public void moveBoardPiece(Integer matchId, Long userId, MovePieceRequest req) {
        logger.info(LOG_MOVE_PIECE, matchId, userId, req.fromX(), req.fromY(), req.toX(), req.toY());

        ensurePlayerInMatch(matchId, userId);

        if (req.fromX().equals(req.toX()) && req.fromY().equals(req.toY())) {
            return;
        }

        if (req.toX() == WHITE_KING_HOME_COL && req.toY() == WHITE_KING_HOME_ROW) {
            throw new IllegalStateException("That square is reserved for your king");
        }

        PlayerInventory piece = inventory
                .findByMatchIdAndUserIdAndPositionXAndPositionY(matchId, userId, req.fromX(), req.fromY())
                .orElseThrow(() -> new IllegalArgumentException("No piece on that square"));

        if (!piece.isOnBoard()) {
            throw new IllegalArgumentException("That square does not hold a board piece");
        }

        if (inventory.existsByMatchIdAndUserIdAndPositionXAndPositionYAndIsOnBoardIsTrue(
                matchId, userId, req.toX(), req.toY())) {
            throw new IllegalStateException("That square is already occupied");
        }

        piece.setPositionX(req.toX());
        piece.setPositionY(req.toY());
        inventory.save(piece);
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