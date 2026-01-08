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
@Table(name = "matches",
        indexes = {
                @Index(name = "idx_matches_status", columnList = "status"),
                @Index(name = "idx_matches_created_at", columnList = "created_at DESC")
        })
public class Match {

    private static final String COLUMN_CURRENT_ROUND = "current_round";
    private static final String COLUMN_WINNER_ID = "winner_id";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_UPDATED_AT = "updated_at";
    private static final String COLUMN_FINISHED_AT = "finished_at";

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_FINISHED = "FINISHED";

    private static final String ERROR_INVALID_STATE_TRANSITION = "Cannot start match that is not in WAITING state";

    private static final int DEFAULT_ROUND = 1;
    private static final int STATUS_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = STATUS_MAX_LENGTH)
    private String status = STATUS_WAITING;

    @Column(name = COLUMN_CURRENT_ROUND, nullable = false)
    private int currentRound = DEFAULT_ROUND;

    @Column(name = COLUMN_WINNER_ID)
    private Long winnerId;

    @Column(name = COLUMN_CREATED_AT, nullable = false)
    private Instant createdAt;

    @Column(name = COLUMN_UPDATED_AT, nullable = false)
    private Instant updatedAt;

    @Column(name = COLUMN_FINISHED_AT)
    private Instant finishedAt;

    // Business methods for state transitions
    public void start() {
        if (!STATUS_WAITING.equals(this.status)) {
            throw new IllegalStateException(ERROR_INVALID_STATE_TRANSITION);
        }
        this.status = STATUS_IN_PROGRESS;
    }

    public void finish(Long winnerId) {
        if (!STATUS_IN_PROGRESS.equals(this.status)) {
            throw new IllegalStateException("Cannot finish match that is not in progress");
        }
        this.status = STATUS_FINISHED;
        this.winnerId = winnerId;
        this.finishedAt = Instant.now();
    }

    public boolean isWaiting() {
        return STATUS_WAITING.equals(this.status);
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(this.status);
    }

    public boolean isFinished() {
        return STATUS_FINISHED.equals(this.status);
    }

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