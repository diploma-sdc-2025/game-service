package org.java.diploma.service.game.chess;

import org.java.diploma.service.game.dto.BoardPieceResponse;
import org.java.diploma.service.game.dto.KingSquareResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Builds a minimal FEN (board + side to move + no castling + no ep) from two players’ placements.
 * Coordinates match the game API: x = file a–h (0–7), y = row with rank 8 at y = 0.
 */
public final class ChessFenBuilder {

    private static final Logger log = LoggerFactory.getLogger(ChessFenBuilder.class);

    private static final String SUFFIX = " w - - 0 1";

    private ChessFenBuilder() {
    }

    public static String build(
            List<BoardPieceResponse> whitePieces,
            KingSquareResponse whiteKing,
            List<BoardPieceResponse> blackPieces,
            KingSquareResponse blackKing
    ) {
        char[][] cells = new char[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                cells[y][x] = '.';
            }
        }

        for (BoardPieceResponse p : whitePieces) {
            char ch = toWhiteChar(p.piece());
            if (ch != 0) {
                cells[p.y()][p.x()] = ch;
            }
        }
        cells[whiteKing.y()][whiteKing.x()] = 'K';

        for (BoardPieceResponse p : blackPieces) {
            char ch = toBlackChar(p.piece());
            if (ch == 0) {
                continue;
            }
            if (cells[p.y()][p.x()] != '.') {
                log.warn("Skipping black piece on occupied square ({}, {})", p.x(), p.y());
                continue;
            }
            cells[p.y()][p.x()] = ch;
        }
        if (cells[blackKing.y()][blackKing.x()] == '.'
                || Character.isLowerCase(cells[blackKing.y()][blackKing.x()])) {
            cells[blackKing.y()][blackKing.x()] = 'k';
        } else {
            log.warn("Black king square ({}, {}) occupied; forcing k", blackKing.x(), blackKing.y());
            cells[blackKing.y()][blackKing.x()] = 'k';
        }

        return buildBoardPart(cells) + SUFFIX;
    }

    private static String buildBoardPart(char[][] cells) {
        StringBuilder fen = new StringBuilder();
        for (int y = 0; y < 8; y++) {
            if (y > 0) {
                fen.append('/');
            }
            int emptyRun = 0;
            for (int x = 0; x < 8; x++) {
                char c = cells[y][x];
                if (c == '.') {
                    emptyRun++;
                } else {
                    if (emptyRun > 0) {
                        fen.append(emptyRun);
                        emptyRun = 0;
                    }
                    fen.append(c);
                }
            }
            if (emptyRun > 0) {
                fen.append(emptyRun);
            }
        }
        return fen.toString();
    }

    private static char toWhiteChar(String pieceKey) {
        if (pieceKey == null) {
            return 0;
        }
        return switch (pieceKey.trim().toLowerCase()) {
            case "pawn" -> 'P';
            case "knight" -> 'N';
            case "bishop" -> 'B';
            case "rook" -> 'R';
            case "queen" -> 'Q';
            default -> 0;
        };
    }

    private static char toBlackChar(String pieceKey) {
        if (pieceKey == null) {
            return 0;
        }
        return switch (pieceKey.trim().toLowerCase()) {
            case "pawn" -> 'p';
            case "knight" -> 'n';
            case "bishop" -> 'b';
            case "rook" -> 'r';
            case "queen" -> 'q';
            default -> 0;
        };
    }
}
