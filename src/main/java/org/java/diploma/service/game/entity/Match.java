package org.java.diploma.service.game.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "matches",
        indexes = {
                @Index(name = "idx_matches_status", columnList = "status"),
                @Index(name = "idx_matches_created_at", columnList = "created_at DESC")
        })
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String status = "WAITING"; // WAITING, IN_PROGRESS, FINISHED

    @Column(name = "current_round", nullable = false)
    private int currentRound = 1;

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
