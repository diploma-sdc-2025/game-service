package org.java.diploma.service.game.dto;

import java.util.List;

public record ShopStateResponse(
        int money,
        List<ShopItemResponse> items,
        List<BenchSlotResponse> bench,
        List<BoardPieceResponse> board
) {}
