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
@Table(name = MatchPlayer.TABLE_NAME,
        uniqueConstraints = @UniqueConstraint(
                name = MatchPlayer.CONSTRAINT_UNIQUE_MATCH_PLAYER,
                columnNames = {MatchPlayer.COLUMN_MATCH_ID, MatchPlayer.COLUMN_USER_ID}
        ))
public class MatchPlayer {

    static final String TABLE_NAME = "match_players";
    static final String CONSTRAINT_UNIQUE_MATCH_PLAYER = "uq_match_players";
    static final String COLUMN_MATCH_ID = "match_id";
    static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_JOINED_AT = "joined_at";
    private static final String COLUMN_IS_ALIVE = "is_alive";
    private static final String COLUMN_PLACEMENT = "placement";

    private static final boolean DEFAULT_ALIVE = true;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = COLUMN_MATCH_ID, nullable = false)
    private Integer matchId;

    @Column(name = COLUMN_USER_ID, nullable = false)
    private Long userId;

    @Column(name = COLUMN_JOINED_AT, nullable = false)
    private Instant joinedAt;

    @Column(name = COLUMN_IS_ALIVE, nullable = false)
    private boolean alive = DEFAULT_ALIVE;

    @Column(name = COLUMN_PLACEMENT)
    private Integer placement;

    @PrePersist
    void onCreate() {
        joinedAt = Instant.now();
    }
}