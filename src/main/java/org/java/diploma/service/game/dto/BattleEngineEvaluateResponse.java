package org.java.diploma.service.game.dto;

import java.util.List;

/**
 * JSON from battle-service {@code POST /api/battle/evaluate}.
 */
public record BattleEngineEvaluateResponse(
        int centipawns,
        String advantage,
        String bestMove,
        List<String> principalVariation
) {
    public BattleEngineEvaluateResponse {
        principalVariation = principalVariation == null ? List.of() : principalVariation;
    }
}
