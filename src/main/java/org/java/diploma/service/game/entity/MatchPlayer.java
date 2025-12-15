package org.java.diploma.service.game.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "match_players",
        uniqueConstraints = @UniqueConstraint(name = "uq_match_players", columnNames = {"match_id", "user_id"}))
public class MatchPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "match_id", nullable = false)
    private Integer matchId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "is_alive", nullable = false)
    private boolean alive = true;

    @Column(name = "placement")
    private Integer placement;

    @PrePersist
    void onCreate() {
        joinedAt = Instant.now();
    }
}
