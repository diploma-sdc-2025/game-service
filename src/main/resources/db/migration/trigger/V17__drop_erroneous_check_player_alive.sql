-- V16 attached check_player_alive() to player_resources, but that table has no hp/is_alive
-- columns (only match_players has is_alive). Updates to gold then failed at commit.
DROP TRIGGER IF EXISTS trg_check_player_alive ON player_resources;
DROP FUNCTION IF EXISTS check_player_alive();
