package org.java.diploma.service.game.dto;

import java.util.List;

public record ShopStateResponse(
        int money,
        int hp,
        int hpMax,
        List<ShopItemResponse> items,
        List<BenchSlotResponse> bench,
        List<BoardPieceResponse> board,
        KingSquareResponse king,
        /** Epoch milliseconds (UTC): when the shared shop placement window ends for this match cycle. */
        long shopPhaseEndsAt
) {}
