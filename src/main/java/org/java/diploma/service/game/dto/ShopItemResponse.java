package org.java.diploma.service.game.dto;

public record ShopItemResponse(
        String piece,
        int cost,
        boolean affordable,
        int owned
) {}
