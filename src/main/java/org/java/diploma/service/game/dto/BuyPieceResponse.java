package org.java.diploma.service.game.dto;

public record BuyPieceResponse(
        String piece,
        int moneyBefore,
        int moneyAfter,
        int slot
) {}
