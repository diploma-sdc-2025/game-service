package org.java.diploma.service.game.repository;

import org.java.diploma.service.game.entity.MatchPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, Integer> {
    List<MatchPlayer> findAllByMatchId(Integer matchId);
    boolean existsByMatchIdAndUserId(Integer matchId, Long userId);
}

