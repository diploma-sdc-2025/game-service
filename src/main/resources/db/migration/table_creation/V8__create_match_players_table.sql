CREATE TABLE match_players (
                               id SERIAL PRIMARY KEY,
                               match_id INT NOT NULL,
                               user_id BIGINT NOT NULL,
                               joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               is_alive BOOLEAN NOT NULL DEFAULT TRUE,
                               placement INT,

                               CONSTRAINT fk_match_players_match_id
                                   FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,

                               CONSTRAINT uq_match_players UNIQUE (match_id, user_id)
);

CREATE INDEX idx_match_players_match_id
    ON match_players (match_id);

CREATE INDEX idx_match_players_alive
    ON match_players (match_id)
    WHERE is_alive = TRUE;

CREATE INDEX idx_match_players_placement
    ON match_players (match_id, placement);
