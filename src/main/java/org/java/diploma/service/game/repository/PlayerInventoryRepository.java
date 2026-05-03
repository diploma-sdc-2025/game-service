package org.java.diploma.service.game.repository;

import org.java.diploma.service.game.entity.PlayerInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerInventoryRepository extends JpaRepository<PlayerInventory, Integer> {
    List<PlayerInventory> findAllByMatchIdAndUserId(Integer matchId, Long userId);

    boolean existsByMatchIdAndUserIdAndPositionXAndPositionY(
            Integer matchId, Long userId, int positionX, int positionY);

    boolean existsByMatchIdAndUserIdAndPositionXAndPositionYAndIsOnBoardIsTrue(
            Integer matchId, Long userId, int positionX, int positionY);

    Optional<PlayerInventory> findByMatchIdAndUserIdAndPositionXAndPositionY(
            Integer matchId, Long userId, int positionX, int positionY);

    void deleteAllByMatchIdAndUserId(Integer matchId, Long userId);
}
