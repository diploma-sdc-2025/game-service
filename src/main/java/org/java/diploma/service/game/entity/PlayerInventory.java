package org.java.diploma.service.game.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = PlayerInventory.TABLE_NAME)
public class PlayerInventory {

    static final String TABLE_NAME = "player_inventory";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "match_id", nullable = false)
    private Integer matchId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "piece_id", nullable = false)
    private Integer pieceId;

    @Column(name = "position_x", nullable = false)
    private int positionX;

    @Column(name = "position_y", nullable = false)
    private int positionY;

    @Column(name = "is_on_board", nullable = false)
    private boolean isOnBoard;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    @PrePersist
    void onCreate() {
        if (acquiredAt == null) acquiredAt = Instant.now();
    }
}
