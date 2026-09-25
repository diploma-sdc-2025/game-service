package org.java.diploma.service.game.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java.diploma.service.game.dto.BenchSlotResponse;
import org.java.diploma.service.game.dto.BoardPieceResponse;
import org.java.diploma.service.game.dto.BuyPieceRequest;
import org.java.diploma.service.game.dto.KingSquareResponse;
import org.java.diploma.service.game.dto.MatchResponse;
import org.java.diploma.service.game.dto.PlacePieceRequest;
import org.java.diploma.service.game.dto.ShopStateResponse;
import org.java.diploma.service.game.entity.Match;
import org.java.diploma.service.game.repository.MatchPlayerRepository;
import org.java.diploma.service.game.service.GameService;
import org.java.diploma.service.game.service.GameStateRedisService;
import org.java.diploma.service.game.service.MatchBattleEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plays the fill-opponent seat: buy/place during shop, and trigger the shared battle eval when the timer ends
 * so a solo match does not stall if the client is slow.
 */
@Component
@ConditionalOnProperty(name = "autochess.bot.enabled", havingValue = "true", matchIfMissing = true)
public class BotOpponentService {

    private static final Logger log = LoggerFactory.getLogger(BotOpponentService.class);

    private static final int PAWN_ROW = 6;
    private static final int MINOR_ROW = 5;

    private final MatchPlayerRepository matchPlayers;
    private final GameService game;
    private final MatchBattleEvaluationService battleEvaluation;
    private final GameStateRedisService redisState;
    private final ObjectMapper objectMapper;

    public BotOpponentService(
            MatchPlayerRepository matchPlayers,
            GameService game,
            MatchBattleEvaluationService battleEvaluation,
            GameStateRedisService redisState,
            ObjectMapper objectMapper
    ) {
        this.matchPlayers = matchPlayers;
        this.game = game;
        this.battleEvaluation = battleEvaluation;
        this.redisState = redisState;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${autochess.bot.tick-ms:1500}")
    public void tick() {
        List<Integer> matchIds = matchPlayers.findMatchIdsByUserIdAndStatus(
                BotPlayers.USER_ID, Match.STATUS_IN_PROGRESS);
        for (Integer matchId : matchIds) {
            try {
                play(matchId);
            } catch (Exception e) {
                log.debug("Fill opponent tick skipped matchId={}: {}", matchId, e.getMessage());
            }
        }
    }

    private void play(Integer matchId) {
        MatchResponse match = game.getMatch(matchId);
        if (match == null || Match.STATUS_FINISHED.equals(match.status())) {
            return;
        }
        if (isBattleReplayOpen(matchId)) {
            return;
        }

        ShopStateResponse shop = game.getShopState(matchId, BotPlayers.USER_ID);
        long now = System.currentTimeMillis();
        if (now >= shop.shopPhaseEndsAt()) {
            battleEvaluation.evaluateRound(matchId, BotPlayers.USER_ID);
            return;
        }

        spendGold(matchId, shop);
        shop = game.getShopState(matchId, BotPlayers.USER_ID);
        placeBench(matchId, shop);
    }

    private boolean isBattleReplayOpen(Integer matchId) {
        return redisState.getLastBattleEval(matchId)
                .map(last -> {
                    try {
                        JsonNode node = objectMapper.readTree(last.json());
                        JsonNode ends = node.get("battleViewEndsAt");
                        if (ends == null || !ends.canConvertToLong()) {
                            return false;
                        }
                        return ends.asLong() > System.currentTimeMillis();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .orElse(false);
    }

    private void spendGold(Integer matchId, ShopStateResponse shop) {
        int gold = shop.money();
        int pawnOwned = owned(shop, "pawn");
        while (gold >= 1 && pawnOwned < 5) {
            try {
                game.buyPiece(matchId, BotPlayers.USER_ID, new BuyPieceRequest("pawn", null));
                gold -= 1;
                pawnOwned += 1;
            } catch (RuntimeException e) {
                break;
            }
        }
        if (gold >= 3) {
            try {
                game.buyPiece(matchId, BotPlayers.USER_ID, new BuyPieceRequest("knight", null));
            } catch (RuntimeException ignored) {
                /* bench full or shop closed */
            }
        }
    }

    private void placeBench(Integer matchId, ShopStateResponse shop) {
        List<BenchSlotResponse> bench = shop.bench() == null ? List.of() : shop.bench();
        for (BenchSlotResponse slot : bench) {
            int[] square = nextEmptySquare(shop, slot.piece());
            if (square == null) {
                continue;
            }
            try {
                game.placePieceFromBench(
                        matchId,
                        BotPlayers.USER_ID,
                        new PlacePieceRequest(slot.slot(), square[0], square[1]));
                shop = withOccupiedSquare(shop, square[0], square[1], slot.piece());
            } catch (RuntimeException e) {
                log.debug("Fill opponent could not place {} in match {}: {}", slot.piece(), matchId, e.getMessage());
            }
        }
    }

    static int owned(ShopStateResponse shop, String piece) {
        if (shop.items() == null) {
            return 0;
        }
        return shop.items().stream()
                .filter(i -> piece.equalsIgnoreCase(i.piece()))
                .mapToInt(i -> i.owned())
                .findFirst()
                .orElse(0);
    }

    static int[] nextEmptySquare(ShopStateResponse shop, String piece) {
        Set<String> taken = occupiedKeys(shop);
        int preferredRow = "pawn".equalsIgnoreCase(piece) ? PAWN_ROW : MINOR_ROW;
        int[] rows = "pawn".equalsIgnoreCase(piece)
                ? new int[] {PAWN_ROW, 5, 4}
                : new int[] {MINOR_ROW, 4, 6};
        for (int y : rows) {
            if ("pawn".equalsIgnoreCase(piece) && (y < 4 || y > 6)) {
                continue;
            }
            for (int x = 0; x <= 7; x++) {
                String key = x + ":" + y;
                if (!taken.contains(key)) {
                    return new int[] {x, y};
                }
            }
        }
        return null;
    }

    private static Set<String> occupiedKeys(ShopStateResponse shop) {
        Set<String> taken = new HashSet<>();
        KingSquareResponse king = shop.king();
        if (king != null) {
            taken.add(king.x() + ":" + king.y());
        }
        if (shop.board() != null) {
            for (BoardPieceResponse p : shop.board()) {
                taken.add(p.x() + ":" + p.y());
            }
        }
        return taken;
    }

    private static ShopStateResponse withOccupiedSquare(ShopStateResponse shop, int x, int y, String piece) {
        List<BoardPieceResponse> board = new java.util.ArrayList<>(
                shop.board() == null ? List.of() : shop.board());
        board.add(new BoardPieceResponse(x, y, piece));
        return new ShopStateResponse(
                shop.money(),
                shop.hp(),
                shop.hpMax(),
                shop.items(),
                shop.bench(),
                board,
                shop.king(),
                shop.shopPhaseEndsAt());
    }
}
