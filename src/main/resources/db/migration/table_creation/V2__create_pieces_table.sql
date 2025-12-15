CREATE TABLE pieces (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(50) NOT NULL UNIQUE,
                        type VARCHAR(50) NOT NULL,
                        cost_gold INT NOT NULL,
                        description TEXT,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pieces_type ON pieces(type);
CREATE INDEX idx_pieces_name ON pieces(name);