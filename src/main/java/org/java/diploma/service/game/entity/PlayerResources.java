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
@Table(name = PlayerResources.TABLE_NAME,
        uniqueConstraints = @UniqueConstraint(
                name = PlayerResources.CONSTRAINT_UNIQUE_PLAYER_RESOURCES,
                columnNames = {PlayerResources.COLUMN_MATCH_ID, PlayerResources.COLUMN_USER_ID}
        ))
public class PlayerResources {

    static final String TABLE_NAME = "player_resources";
    static final String CONSTRAINT_UNIQUE_PLAYER_RESOURCES = "uq_player_resources_match_user";
    static final String COLUMN_MATCH_ID = "match_id";
    static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_GOLD = "gold";
    private static final String COLUMN_LEVEL = "level";
    private static final String COLUMN_EXPERIENCE = "experience";
    private static final String COLUMN_HP = "hp";
    private static final String COLUMN_UPDATED_AT = "updated_at";

    public static final int DEFAULT_GOLD = 0;
    public static final int DEFAULT_LEVEL = 1;
    public static final int DEFAULT_EXPERIENCE = 0;
    /** Starting / max HP for the vertical HP bar (battle damage subtracts from this). */
    public static final int DEFAULT_HP = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = COLUMN_MATCH_ID, nullable = false)
    private Integer matchId;

    @Column(name = COLUMN_USER_ID, nullable = false)
    private Long userId;

    @Column(name = COLUMN_GOLD, nullable = false)
    private int gold = DEFAULT_GOLD;

    @Column(name = COLUMN_LEVEL, nullable = false)
    private int level = DEFAULT_LEVEL;

    @Column(name = COLUMN_EXPERIENCE, nullable = false)
    private int experience = DEFAULT_EXPERIENCE;

    @Column(name = COLUMN_HP, nullable = false)
    private int hp = DEFAULT_HP;

    @Column(name = COLUMN_UPDATED_AT, nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}