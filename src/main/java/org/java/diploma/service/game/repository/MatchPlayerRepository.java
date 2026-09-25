package org.java.diploma.service.game.repository;

import org.java.diploma.service.game.entity.MatchPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, Integer> {
    List<MatchPlayer> findAllByMatchId(Integer matchId);
    boolean existsByMatchIdAndUserId(Integer matchId, Long userId);

    @Query("""
            SELECT mp.matchId FROM MatchPlayer mp, Match m
            WHERE mp.matchId = m.id AND mp.userId = :userId AND m.status = :status
            """)
    List<Integer> findMatchIdsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
}

