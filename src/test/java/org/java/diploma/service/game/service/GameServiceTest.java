package org.java.diploma.service.game.service;

import org.java.diploma.service.game.dto.CreateMatchRequest;
import org.java.diploma.service.game.dto.MatchResponse;
import org.java.diploma.service.game.entity.Match;
import org.java.diploma.service.game.entity.MatchPlayer;
import org.java.diploma.service.game.repository.MatchPlayerRepository;
import org.java.diploma.service.game.repository.MatchRepository;
import org.java.diploma.service.game.repository.PlayerResourcesRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameServiceTest {

    @Test
    void createMatch_createsMatchPlayersResources_andInitializesRedisState() {
        var matches = mock(MatchRepository.class);
        var matchPlayers = mock(MatchPlayerRepository.class);
        var resources = mock(PlayerResourcesRepository.class);
        var redisState = mock(GameStateRedisService.class);

        when(matches.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(123);
            return m;
        });

        var sut = new GameService(matches, matchPlayers, resources, redisState);

        var req = new CreateMatchRequest(List.of(10L, 20L, 30L));
        MatchResponse res = sut.createMatch(req);

        assertThat(res.matchId()).isEqualTo(123);
        assertThat(res.status()).isEqualTo("WAITING");
        assertThat(res.currentRound()).isEqualTo(1);
        assertThat(res.players()).containsExactly(10L, 20L, 30L);

        verify(redisState).initMatchState(123);

        ArgumentCaptor<MatchPlayer> mpCaptor = ArgumentCaptor.forClass(MatchPlayer.class);
        verify(matchPlayers, times(3)).save(mpCaptor.capture());
        assertThat(mpCaptor.getAllValues())
                .extracting(MatchPlayer::getMatchId, MatchPlayer::getUserId)
                .containsExactly(
                        tuple(123, 10L),
                        tuple(123, 20L),
                        tuple(123, 30L)
                );

        verify(resources, times(3)).save(any());
    }

    @Test
    void getMatch_throwsWhenNotFound() {
        var matches = mock(MatchRepository.class);
        var matchPlayers = mock(MatchPlayerRepository.class);
        var resources = mock(PlayerResourcesRepository.class);
        var redisState = mock(GameStateRedisService.class);

        when(matches.findById(999)).thenReturn(Optional.empty());

        var sut = new GameService(matches, matchPlayers, resources, redisState);

        assertThatThrownBy(() -> sut.getMatch(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Match not found");
    }

    @Test
    void startMatch_throwsWhenStatusNotWaiting() {
        var matches = mock(MatchRepository.class);
        var matchPlayers = mock(MatchPlayerRepository.class);
        var resources = mock(PlayerResourcesRepository.class);
        var redisState = mock(GameStateRedisService.class);

        Match m = new Match();
        m.setId(1);
        m.setStatus("IN_PROGRESS");
        when(matches.findById(1)).thenReturn(Optional.of(m));

        var sut = new GameService(matches, matchPlayers, resources, redisState);

        assertThatThrownBy(() -> sut.startMatch(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WAITING");

        verify(matches, never()).save(any());
    }

    @Test
    void startMatch_updatesWaitingToInProgress() {
        var matches = mock(MatchRepository.class);
        var matchPlayers = mock(MatchPlayerRepository.class);
        var resources = mock(PlayerResourcesRepository.class);
        var redisState = mock(GameStateRedisService.class);

        Match m = new Match();
        m.setId(1);
        m.setStatus("WAITING");
        when(matches.findById(1)).thenReturn(Optional.of(m));

        var sut = new GameService(matches, matchPlayers, resources, redisState);

        sut.startMatch(1);

        assertThat(m.getStatus()).isEqualTo("IN_PROGRESS");
        verify(matches).save(m);
    }

    @Test
    void getMatch_returnsMatchResponse_whenMatchExists() {
        var matches = mock(MatchRepository.class);
        var matchPlayers = mock(MatchPlayerRepository.class);
        var resources = mock(PlayerResourcesRepository.class);
        var redisState = mock(GameStateRedisService.class);

        Match m = new Match();
        m.setId(5);
        m.setStatus("WAITING");
        m.setCurrentRound(2);

        when(matches.findById(5)).thenReturn(Optional.of(m));
        when(matchPlayers.findAllByMatchId(5)).thenReturn(List.of(
                player(10L),
                player(20L)
        ));

        var sut = new GameService(matches, matchPlayers, resources, redisState);

        MatchResponse res = sut.getMatch(5);

        assertThat(res.matchId()).isEqualTo(5);
        assertThat(res.status()).isEqualTo("WAITING");
        assertThat(res.currentRound()).isEqualTo(2);
        assertThat(res.players()).containsExactly(10L, 20L);
    }

    private MatchPlayer player(long userId) {
        MatchPlayer mp = new MatchPlayer();
        mp.setUserId(userId);
        return mp;
    }



}
