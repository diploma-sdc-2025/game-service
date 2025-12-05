CREATE TABLE matches (
                         id SERIAL PRIMARY KEY,
                         status VARCHAR(50) NOT NULL DEFAULT 'WAITING',
                         current_round INT NOT NULL DEFAULT 1,
                         winner_id BIGINT,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         finished_at TIMESTAMP,

    CONSTRAINT chk_matches_current_round CHECK (current_round > 0),
    CONSTRAINT chk_matches_status CHECK (
        status IN ('WAITING', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED')
    )
);

CREATE INDEX idx_matches_status ON matches(status);
CREATE INDEX idx_matches_created_at ON matches(created_at DESC);