package org.java.diploma.service.game.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchBattleEvaluationColorTest {

    private static final int ROUND = 3;

    @Test
    void resolveWhiteUserId_stableForSameMatchRoundAndPairRegardlessOfArgumentOrder() {
        long white = MatchBattleEvaluationService.resolveWhiteUserId(7, ROUND, 10L, 20L);
        assertThat(white).isIn(10L, 20L);
        assertThat(MatchBattleEvaluationService.resolveWhiteUserId(7, ROUND, 10L, 20L)).isEqualTo(white);
        assertThat(MatchBattleEvaluationService.resolveWhiteUserId(7, ROUND, 20L, 10L)).isEqualTo(white);
        assertThat(MatchBattleEvaluationService.resolveWhiteUserId(9, ROUND, 100L, 200L))
                .isEqualTo(MatchBattleEvaluationService.resolveWhiteUserId(9, ROUND, 200L, 100L));
    }

    @Test
    void resolveWhiteUserId_variesAcrossMatchIds_forSameParticipantPairAndRound() {
        int lows = 0;
        int highs = 0;
        for (int mid = 1; mid <= 64; mid++) {
            long w = MatchBattleEvaluationService.resolveWhiteUserId(mid, ROUND, 100L, 200L);
            assertThat(w).isIn(100L, 200L);
            if (w == 100L) {
                lows++;
            } else {
                highs++;
            }
        }
        assertThat(lows).isGreaterThan(0);
        assertThat(highs).isGreaterThan(0);
    }

    @Test
    void resolveWhiteUserId_swapsSidesAcrossShopRounds_forSameMatchAndPlayers() {
        int lows = 0;
        for (int round = 1; round <= 64; round++) {
            long w = MatchBattleEvaluationService.resolveWhiteUserId(42, round, 10L, 20L);
            assertThat(w).isIn(10L, 20L);
            if (w == 10L) {
                lows++;
            }
        }
        assertThat(lows).isBetween(1, 63);
    }
}
