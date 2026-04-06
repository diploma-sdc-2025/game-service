package org.java.diploma.service.game.repository;


import org.java.diploma.service.game.entity.PlayerResources;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerResourcesRepository extends JpaRepository<PlayerResources, Integer> {
    List<PlayerResources> findAllByMatchId(Integer matchId);
    Optional<PlayerResources> findByMatchIdAndUserId(Integer matchId, Long userId);
}
