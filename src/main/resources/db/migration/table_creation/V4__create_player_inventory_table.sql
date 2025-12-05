CREATE TABLE player_inventory (
                                  id SERIAL PRIMARY KEY,
                                  match_id INT NOT NULL,
                                  user_id BIGINT NOT NULL,
                                  piece_id INT NOT NULL,
                                  position_x INT NOT NULL,
                                  position_y INT NOT NULL,
                                  is_on_board BOOLEAN NOT NULL DEFAULT FALSE,
                                  acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT fk_player_inventory_match_id
                                      FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_player_inventory_piece_id
                                      FOREIGN KEY (piece_id) REFERENCES pieces(id) ON DELETE RESTRICT,

                                  CONSTRAINT chk_player_inventory_position_x CHECK (position_x >= 0 AND position_x < 8),
                                  CONSTRAINT chk_player_inventory_position_y CHECK (position_y >= 0 AND position_y < 8),

                                  CONSTRAINT uq_player_inventory_position
                                      UNIQUE (match_id, user_id, position_x, position_y)
);

CREATE INDEX idx_player_inventory_match_id ON player_inventory(match_id);
CREATE INDEX idx_player_inventory_user_id ON player_inventory(user_id);
CREATE INDEX idx_player_inventory_piece_id ON player_inventory(piece_id);
CREATE INDEX idx_player_inventory_board ON player_inventory(match_id, user_id, is_on_board);
