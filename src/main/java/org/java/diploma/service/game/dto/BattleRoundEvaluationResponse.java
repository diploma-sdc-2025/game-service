package org.java.diploma.service.game.dto;

import java.util.List;

public record BattleRoundEvaluationResponse(
        String fen,
        int centipawns,
        String advantage,
        long whiteUserId,
        long blackUserId,
        boolean currentUserIsWhite,
        List<BoardPieceResponse> whiteBoard,
        List<BoardPieceResponse> blackBoard,
        KingSquareResponse whiteKing,
        KingSquareResponse blackKing,
        List<String> principalVariation,
        int whiteHp,
        int blackHp
) {
    /**
     * Redis-cached payloads may have been built for another player; re-bind the viewer flag for this requester.
     */
    public BattleRoundEvaluationResponse forViewer(long userId) {
        return new BattleRoundEvaluationResponse(
                fen,
                centipawns,
                advantage,
                whiteUserId,
                blackUserId,
                userId == whiteUserId,
                whiteBoard,
                blackBoard,
                whiteKing,
                blackKing,
                principalVariation,
                whiteHp,
                blackHp
        );
    }
}
