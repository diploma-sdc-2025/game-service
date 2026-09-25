package org.java.diploma.service.game.bot;

import org.java.diploma.service.game.dto.BoardPieceResponse;
import org.java.diploma.service.game.dto.KingSquareResponse;
import org.java.diploma.service.game.dto.ShopItemResponse;
import org.java.diploma.service.game.dto.ShopStateResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BotOpponentServiceTest {

    @Test
    void nextEmptySquare_skipsKingAndOccupied() {
        ShopStateResponse shop = new ShopStateResponse(
                2,
                30,
                30,
                List.of(new ShopItemResponse("pawn", 1, true, 1)),
                List.of(),
                List.of(new BoardPieceResponse(0, 6, "pawn")),
                new KingSquareResponse(4, 7),
                System.currentTimeMillis() + 20_000L);
        int[] sq = BotOpponentService.nextEmptySquare(shop, "pawn");
        assertThat(sq).isNotNull();
        assertThat(sq[0]).isNotEqualTo(0);
        assertThat(sq[1]).isEqualTo(6);
    }

    @Test
    void owned_readsShopItem() {
        ShopStateResponse shop = new ShopStateResponse(
                2, 30, 30,
                List.of(new ShopItemResponse("pawn", 1, true, 3)),
                List.of(),
                List.of(),
                new KingSquareResponse(4, 7),
                0L);
        assertThat(BotOpponentService.owned(shop, "pawn")).isEqualTo(3);
    }
}
