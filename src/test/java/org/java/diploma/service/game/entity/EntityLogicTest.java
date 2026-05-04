package org.java.diploma.service.game.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityLogicTest {

    @Test
    void matchStateTransitionsAndLifecycleHooks() {
        Match match = new Match();
        assertThat(match.isWaiting()).isTrue();
        assertThat(match.isInProgress()).isFalse();
        assertThat(match.isFinished()).isFalse();

        match.onCreate();
        assertThat(match.getCreatedAt()).isNotNull();
        assertThat(match.getUpdatedAt()).isNotNull();

        match.start();
        assertThat(match.isInProgress()).isTrue();

        match.finish(77L);
        assertThat(match.isFinished()).isTrue();
        assertThat(match.getWinnerId()).isEqualTo(77L);
        assertThat(match.getFinishedAt()).isNotNull();

        match.onUpdate();
        assertThat(match.getUpdatedAt()).isNotNull();
    }

    @Test
    void matchRejectsInvalidStateTransitions() {
        Match alreadyStarted = new Match();
        alreadyStarted.setStatus(Match.STATUS_IN_PROGRESS);
        assertThatThrownBy(alreadyStarted::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot start match");

        Match notInProgress = new Match();
        assertThatThrownBy(() -> notInProgress.finish(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot finish match");
    }

    @Test
    void pieceAndMatchPlayerAccessorsWork() {
        Piece piece = new Piece();
        piece.setId(1);
        piece.setName("Pawn");
        piece.setType("PAWN");
        piece.setCostGold(1);
        assertThat(piece.getId()).isEqualTo(1);
        assertThat(piece.getName()).isEqualTo("Pawn");
        assertThat(piece.getType()).isEqualTo("PAWN");
        assertThat(piece.getCostGold()).isEqualTo(1);

        MatchPlayer mp = new MatchPlayer();
        mp.setId(5);
        mp.setMatchId(10);
        mp.setUserId(20L);
        mp.setAlive(false);
        mp.setPlacement(2);
        mp.onCreate();
        assertThat(mp.getId()).isEqualTo(5);
        assertThat(mp.getMatchId()).isEqualTo(10);
        assertThat(mp.getUserId()).isEqualTo(20L);
        assertThat(mp.isAlive()).isFalse();
        assertThat(mp.getPlacement()).isEqualTo(2);
        assertThat(mp.getJoinedAt()).isNotNull();
    }

    @Test
    void inventoryAndResourcesLifecycleAndAccessors() {
        PlayerInventory inv = new PlayerInventory();
        inv.setId(3);
        inv.setMatchId(100);
        inv.setUserId(200L);
        inv.setPieceId(4);
        inv.setPositionX(2);
        inv.setPositionY(8);
        inv.setOnBoard(false);
        inv.onCreate();
        assertThat(inv.getId()).isEqualTo(3);
        assertThat(inv.getMatchId()).isEqualTo(100);
        assertThat(inv.getUserId()).isEqualTo(200L);
        assertThat(inv.getPieceId()).isEqualTo(4);
        assertThat(inv.getPositionX()).isEqualTo(2);
        assertThat(inv.getPositionY()).isEqualTo(8);
        assertThat(inv.isOnBoard()).isFalse();
        assertThat(inv.getAcquiredAt()).isNotNull();

        Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
        inv.setAcquiredAt(fixed);
        inv.onCreate();
        assertThat(inv.getAcquiredAt()).isEqualTo(fixed);

        PlayerResources res = new PlayerResources();
        res.setId(7);
        res.setMatchId(100);
        res.setUserId(200L);
        res.setGold(12);
        res.setLevel(3);
        res.setExperience(9);
        res.setHp(27);
        res.onCreate();
        assertThat(res.getId()).isEqualTo(7);
        assertThat(res.getMatchId()).isEqualTo(100);
        assertThat(res.getUserId()).isEqualTo(200L);
        assertThat(res.getGold()).isEqualTo(12);
        assertThat(res.getLevel()).isEqualTo(3);
        assertThat(res.getExperience()).isEqualTo(9);
        assertThat(res.getHp()).isEqualTo(27);
        assertThat(res.getUpdatedAt()).isNotNull();

        Instant before = res.getUpdatedAt();
        res.onUpdate();
        assertThat(res.getUpdatedAt()).isAfterOrEqualTo(before);
    }
}
