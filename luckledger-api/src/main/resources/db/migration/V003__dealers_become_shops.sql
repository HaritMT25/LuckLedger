-- Dealers become cross-game shops: a storefront with a named owner, an optional avatar, and a list of
-- the games it stocks (a shop may carry several games, so it is no longer bound to a single game_id).
--
-- The demo game graph is re-seeded deterministically at startup (GameSeeder is idempotent on an empty
-- game table), so we clear the previously seeded games/dealers/books/tickets here and let the app
-- rebuild them under the new shop roster. Player accounts and the append-only ledger are untouched.
DELETE FROM ticket;
DELETE FROM ticket_book;
DELETE FROM dealer;
DELETE FROM game;

-- Reshape the dealer table from "one row per (game, dealer)" to "one row per shop".
ALTER TABLE dealer DROP COLUMN game_id;                 -- also drops its FK and idx_dealer_game
ALTER TABLE dealer RENAME COLUMN name TO shop_name;
ALTER TABLE dealer ADD COLUMN owner_name    VARCHAR(120) NOT NULL DEFAULT '';
ALTER TABLE dealer ADD COLUMN avatar        VARCHAR(255);
ALTER TABLE dealer ADD COLUMN stocked_games JSONB        NOT NULL DEFAULT '[]'::jsonb;
