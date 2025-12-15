package org.java.diploma.service.game.repository;


import org.java.diploma.service.game.entity.PlayerResources;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerResourcesRepository extends JpaRepository<PlayerResources, Integer> {
    List<PlayerResources> findAllByMatchId(Integer matchId);
}
