package org.java.diploma.service.game.chess;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BattleFenRulesTest {

    @Test
    void isBlackKingInCheck_detectsRookAndDiagonalChecks() {
        assertThat(BattleFenRules.isBlackKingInCheck("7k/7Q/8/8/8/8/8/K7 w - - 0 1")).isTrue();
        assertThat(BattleFenRules.isBlackKingInCheck("7k/8/8/8/8/8/8/K7 w - - 0 1")).isFalse();
        assertThat(BattleFenRules.isBlackKingInCheck("")).isFalse();
        assertThat(BattleFenRules.isBlackKingInCheck(null)).isFalse();
    }
}
