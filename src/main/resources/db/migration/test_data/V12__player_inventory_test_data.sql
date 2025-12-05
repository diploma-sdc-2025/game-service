INSERT INTO player_inventory (match_id, user_id, piece_id, position_x, position_y, is_on_board)
VALUES
    -- Match 1
    (1,101,1,0,0,TRUE),(1,101,2,1,0,FALSE),(1,102,3,0,1,TRUE),(1,102,4,1,1,FALSE),(1,103,5,0,2,TRUE),
    (1,104,1,0,3,TRUE),(1,105,2,1,3,FALSE),
    -- Match 2
    (2,106,1,0,0,TRUE),(2,107,2,1,0,FALSE),(2,108,3,0,1,TRUE),(2,109,4,1,1,FALSE),(2,110,5,0,2,TRUE),
    -- Match 3
    (3,111,1,0,0,TRUE),(3,112,2,1,0,FALSE),(3,113,3,0,1,TRUE),(3,114,4,1,1,FALSE),(3,115,5,0,2,TRUE),
    -- Match 4
    (4,116,1,0,0,TRUE),(4,117,2,1,0,FALSE),(4,118,3,0,1,TRUE),(4,119,4,1,1,FALSE),(4,120,5,0,2,TRUE),
    -- Match 5
    (5,121,1,0,0,TRUE),(5,122,2,1,0,FALSE),(5,123,3,0,1,TRUE),(5,124,4,1,1,FALSE),(5,125,5,0,2,TRUE);
