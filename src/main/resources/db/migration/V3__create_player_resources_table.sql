CREATE TABLE player_resources (
                                  id SERIAL PRIMARY KEY,
                                  match_id INT NOT NULL,
                                  user_id BIGINT NOT NULL,
                                  gold INT NOT NULL DEFAULT 0,
                                  level INT NOT NULL DEFAULT 1,
                                  experience INT NOT NULL DEFAULT 0,
                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT fk_player_resources_match_id
                                      FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,

                                  CONSTRAINT chk_player_resources_gold CHECK (gold >= 0),
                                  CONSTRAINT chk_player_resources_level CHECK (level > 0),
                                  CONSTRAINT chk_player_resources_experience CHECK (experience >= 0),
                                  CONSTRAINT uq_player_resources_match_user UNIQUE (match_id, user_id)
);

CREATE INDEX idx_player_resources_match_id ON player_resources(match_id);
CREATE INDEX idx_player_resources_user_id ON player_resources(user_id);