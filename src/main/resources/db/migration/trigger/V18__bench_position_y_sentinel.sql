-- Bench rows used position_y = 0, which clashes with board rank 8 (also y = 0).
-- Reserve y = 8 for all off-board bench pieces.
UPDATE player_inventory
SET position_y = 8
WHERE is_on_board = false
  AND position_y = 0;
