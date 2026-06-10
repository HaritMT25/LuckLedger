-- Tickets remember their buyer so a player can recover unscratched tickets after a refresh.
-- NULL until the ticket is sold; set in the same transaction that marks it SOLD.
ALTER TABLE ticket ADD COLUMN player_id UUID REFERENCES player(id);
CREATE INDEX idx_ticket_player ON ticket(player_id);
