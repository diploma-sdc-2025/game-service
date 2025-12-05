CREATE OR REPLACE FUNCTION check_player_alive()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.hp <= 0 THEN
        NEW.is_alive = FALSE;
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_player_alive
BEFORE UPDATE ON player_resources
FOR EACH ROW
EXECUTE FUNCTION check_player_alive();