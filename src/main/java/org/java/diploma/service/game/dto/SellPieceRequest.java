package org.java.diploma.service.game.dto;

/**
 * Sell a piece from the bench ({@code benchSlot}) or from the board ({@code fromX}, {@code fromY}).
 * Exactly one mode must be provided.
 */
public record SellPieceRequest(Integer benchSlot, Integer fromX, Integer fromY) {}
