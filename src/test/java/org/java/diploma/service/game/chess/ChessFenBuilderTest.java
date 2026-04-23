package org.java.diploma.service.game.chess;

import org.java.diploma.service.game.dto.BoardPieceResponse;
import org.java.diploma.service.game.dto.KingSquareResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChessFenBuilderTest {

    @Test
    void buildsFenWithKingsAndPieces() {
        String fen = ChessFenBuilder.build(
                List.of(new BoardPieceResponse(4, 6, "pawn"), new BoardPieceResponse(3, 7, "queen")),
                new KingSquareResponse(4, 7),
                List.of(new BoardPieceResponse(4, 1, "pawn"), new BoardPieceResponse(3, 0, "queen")),
                new KingSquareResponse(4, 0)
        );

        assertEquals("3qk3/4p3/8/8/8/8/4P3/3QK3 w - - 0 1", fen);
    }

    @Test
    void blackPieceOnOccupiedSquareIsSkippedAndKingForced() {
        String fen = ChessFenBuilder.build(
                List.of(new BoardPieceResponse(4, 7, "rook")),
                new KingSquareResponse(4, 7),
                List.of(new BoardPieceResponse(4, 7, "rook")),
                new KingSquareResponse(4, 7)
        );

        assertTrue(fen.startsWith("8/8/8/8/8/8/8/4k3"));
    }

    @Test
    void unknownOrNullPiecesAreIgnored() {
        String fen = ChessFenBuilder.build(
                List.of(
                        new BoardPieceResponse(0, 0, "dragon"),
                        new BoardPieceResponse(1, 0, null)
                ),
                new KingSquareResponse(4, 7),
                List.of(new BoardPieceResponse(2, 0, "wizard")),
                new KingSquareResponse(4, 0)
        );

        assertEquals("4k3/8/8/8/8/8/8/4K3 w - - 0 1", fen);
    }
}
