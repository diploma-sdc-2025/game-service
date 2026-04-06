package org.java.diploma.service.game.repository;

import org.java.diploma.service.game.entity.Piece;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PieceRepository extends JpaRepository<Piece, Integer> {
    Optional<Piece> findByNameIgnoreCase(String name);
}
