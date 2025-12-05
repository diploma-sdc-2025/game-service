CREATE TABLE pieces (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(50) NOT NULL UNIQUE,
                        type VARCHAR(50) NOT NULL,
                        cost_gold INT NOT NULL,
                        description TEXT,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT chk_pieces_cost_gold CHECK (cost_gold > 0),
                        CONSTRAINT chk_pieces_name_length CHECK (LENGTH(TRIM(name)) > 0),
                        CONSTRAINT chk_pieces_type CHECK (
                            type IN ('PONE', 'KING', 'QUEEN', 'BISHOP', 'KNIGHT', 'ROOK')
                            )
);

CREATE INDEX idx_pieces_type ON pieces(type);
CREATE INDEX idx_pieces_cost_gold ON pieces(cost_gold);
CREATE INDEX idx_pieces_name ON pieces(name);