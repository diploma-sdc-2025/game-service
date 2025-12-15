package org.java.diploma.service.game.dto;

import java.util.List;

public record MatchResponse(
        Integer matchId,
        String status,
        int currentRound,
        List<Long> players
) {}