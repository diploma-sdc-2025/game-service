CREATE TABLE matches (
                         id SERIAL PRIMARY KEY,
                         player1_id BIGINT NOT NULL,
                         player2_id BIGINT NOT NULL,
                         status VARCHAR(50) NOT NULL DEFAULT 'WAITING',
                         current_round INT NOT NULL DEFAULT 1,
                         winner_id BIGINT,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         finished_at TIMESTAMP,

    CONSTRAINT chk_matches_different_players CHECK (player1_id != player2_id),
    CONSTRAINT chk_matches_current_round CHECK (current_round > 0),
    CONSTRAINT chk_matches_winner CHECK (
        winner_id IS NULL OR
        winner_id = player1_id OR
        winner_id = player2_id
    ),
    CONSTRAINT chk_matches_status CHECK (
        status IN ('WAITING', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED')
    )
);

CREATE INDEX idx_matches_player1_id ON matches(player1_id);
CREATE INDEX idx_matches_player2_id ON matches(player2_id);
CREATE INDEX idx_matches_status ON matches(status);
CREATE INDEX idx_matches_created_at ON matches(created_at DESC);