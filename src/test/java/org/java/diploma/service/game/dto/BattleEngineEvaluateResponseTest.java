package org.java.diploma.service.game.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BattleEngineEvaluateResponseTest {

    @Test
    void nullPrincipalVariationDefaultsToEmptyList() {
        BattleEngineEvaluateResponse response = new BattleEngineEvaluateResponse(25, "white", "e2e4", null);
        assertThat(response.principalVariation()).isEmpty();
    }

    @Test
    void keepsProvidedPrincipalVariation() {
        BattleEngineEvaluateResponse response = new BattleEngineEvaluateResponse(25, "white", "e2e4", List.of("e2e4", "e7e5"));
        assertThat(response.principalVariation()).containsExactly("e2e4", "e7e5");
    }
}
