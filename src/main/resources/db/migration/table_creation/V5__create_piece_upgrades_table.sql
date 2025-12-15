CREATE TABLE piece_upgrades (
                                id SERIAL PRIMARY KEY,
                                match_id INT NOT NULL,
                                user_id BIGINT NOT NULL,
                                source_piece1_id INT NOT NULL,
                                source_piece2_id INT NOT NULL,
                                source_piece3_id INT NOT NULL,
                                result_piece_id INT NOT NULL,
                                upgraded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_piece_upgrades_match_id
                                    FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
                                CONSTRAINT fk_piece_upgrades_result_piece
                                    FOREIGN KEY (result_piece_id) REFERENCES pieces(id) ON DELETE RESTRICT
);
CREATE INDEX idx_piece_upgrades_match_id ON piece_upgrades(match_id);
CREATE INDEX idx_piece_upgrades_user_id ON piece_upgrades(user_id);
