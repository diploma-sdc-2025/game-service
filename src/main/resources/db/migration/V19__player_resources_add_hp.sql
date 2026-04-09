-- HP bar: battle evaluation subtracts |centipawns| from the losing side (see GameService).
ALTER TABLE player_resources ADD COLUMN IF NOT EXISTS hp INTEGER NOT NULL DEFAULT 100;
