package org.java.diploma.service.game.chess;

import java.util.Arrays;

/**
 * Helpers for interpreting composite battle positions encoded as a chess FEN string.
 * Uses only the piece placement field — no external chess engine dependency.
 */
public final class BattleFenRules {

    private BattleFenRules() {
    }

    /**
     * Whether Black's king is attacked by at least one White piece in this position (opening check on Black).
     * Invalid or empty FEN yields {@code false} so battle resolution still succeeds.
     */
    public static boolean isBlackKingInCheck(String fen) {
        if (fen == null || fen.isBlank()) {
            return false;
        }
        String[] parts = fen.trim().split("\\s+", 2);
        String placement = parts[0];
        char[][] board = new char[8][8];
        for (char[] row : board) {
            Arrays.fill(row, '.');
        }
        int row = 0;
        int col = 0;
        for (int i = 0; i < placement.length(); i++) {
            char ch = placement.charAt(i);
            if (ch == '/') {
                row++;
                col = 0;
                if (row >= 8) {
                    return false;
                }
                continue;
            }
            if (Character.isDigit(ch)) {
                col += ch - '0';
                if (col > 8) {
                    return false;
                }
                continue;
            }
            if (col >= 8 || row >= 8) {
                return false;
            }
            board[row][col++] = ch;
        }
        int kingRow = -1;
        int kingCol = -1;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == 'k') {
                    if (kingRow >= 0) {
                        return false;
                    }
                    kingRow = r;
                    kingCol = c;
                }
            }
        }
        if (kingRow < 0) {
            return false;
        }
        return whiteAttacksSquare(board, kingRow, kingCol);
    }

    private static boolean whiteAttacksSquare(char[][] board, int tr, int tc) {
        int[][] knight = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
        for (int[] d : knight) {
            int r = tr + d[0];
            int c = tc + d[1];
            if (r >= 0 && r < 8 && c >= 0 && c < 8 && board[r][c] == 'N') {
                return true;
            }
        }
        int[][] king = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
        for (int[] d : king) {
            int r = tr + d[0];
            int c = tc + d[1];
            if (r >= 0 && r < 8 && c >= 0 && c < 8 && board[r][c] == 'K') {
                return true;
            }
        }
        // White pawn attacks one rank toward rank 8 (smaller row index): from (tr+1, tc±1)
        if (tr + 1 < 8) {
            if (tc > 0 && board[tr + 1][tc - 1] == 'P') {
                return true;
            }
            if (tc + 1 < 8 && board[tr + 1][tc + 1] == 'P') {
                return true;
            }
        }
        int[][] orth = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : orth) {
            int r = tr + d[0];
            int c = tc + d[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                char p = board[r][c];
                if (p != '.') {
                    if (p == 'R' || p == 'Q') {
                        return true;
                    }
                    break;
                }
                r += d[0];
                c += d[1];
            }
        }
        int[][] diag = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int[] d : diag) {
            int r = tr + d[0];
            int c = tc + d[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                char p = board[r][c];
                if (p != '.') {
                    if (p == 'B' || p == 'Q') {
                        return true;
                    }
                    break;
                }
                r += d[0];
                c += d[1];
            }
        }
        return false;
    }
}
