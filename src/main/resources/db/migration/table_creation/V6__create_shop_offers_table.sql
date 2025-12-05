CREATE TABLE shop_offers (
                             id SERIAL PRIMARY KEY,
                             match_id INT NOT NULL,
                             round_number INT NOT NULL,
                             piece_id INT NOT NULL,
                             slot_number INT NOT NULL,
                             offered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             purchased_by BIGINT,
                             purchased_at TIMESTAMP,

                             CONSTRAINT fk_shop_offers_match_id
                                 FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
                             CONSTRAINT fk_shop_offers_piece_id
                                 FOREIGN KEY (piece_id) REFERENCES pieces(id) ON DELETE RESTRICT,

                             CONSTRAINT chk_shop_offers_round CHECK (round_number > 0),
                             CONSTRAINT chk_shop_offers_slot CHECK (slot_number >= 1 AND slot_number <= 5),
                             CONSTRAINT chk_shop_offers_purchase CHECK (
                                 (purchased_by IS NULL AND purchased_at IS NULL) OR
                                 (purchased_by IS NOT NULL AND purchased_at IS NOT NULL))

);

CREATE INDEX idx_shop_offers_match_id ON shop_offers(match_id);
CREATE INDEX idx_shop_offers_round ON shop_offers(match_id, round_number);
CREATE INDEX idx_shop_offers_piece_id ON shop_offers(piece_id);
CREATE INDEX idx_shop_offers_available ON shop_offers(match_id, round_number)
    WHERE purchased_by IS NULL;