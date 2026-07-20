-- V007: master campaign control.
--
-- A "campaign" is just a game the operator created at runtime through the same verified generation
-- pipeline the two demo games use — there is no separate table. Three columns let an existing game
-- carry a human name, a lifecycle status, and the exact pool contract it was built from.
--
-- game.name        an operator-chosen display name. NULL for the pre-campaign (legacy) rows, which are
--                  still named from their mechanic (e.g. "Demon Seal") for display.
-- game.status      ACTIVE (sellable, restockable) or RETIRED (withdrawn from sale). NOT NULL, default
--                  ACTIVE so every pre-existing row is live. Retiring flips this only — tickets and
--                  ledger are never touched, so already-sold tickets still reveal and pay.
-- game.pool_contract  the JSON of the PoolContract the game was generated from, so a restock can
--                  regenerate an identical-economics batch without any static config. NULL for legacy
--                  rows (restock falls back to the ApiConfig statics). Stored as a persistence-shaped
--                  document (PoolContractDoc), never the domain PoolContract directly, so its derived
--                  getters do not poison the JSON round-trip. RTP is sacred: the contract is immutable
--                  once stored — retuning economics means creating a new campaign, never editing this.
ALTER TABLE game ADD COLUMN name VARCHAR(120);

ALTER TABLE game ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE game ADD COLUMN pool_contract JSONB;
