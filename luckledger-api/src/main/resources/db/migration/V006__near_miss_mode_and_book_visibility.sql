-- Persist two per-game/per-book education dials that were wired into generation in Phase 2.1/2.2 but
-- not yet stored.
--
-- game.near_miss_mode records which mode the pool was GENERATED with (CLEAN = losers left plain;
-- REALISTIC = a DESIGN-mandated share of losers engineered one step short of a win). It drives how the
-- awareness layer honestly narrates the manufactured "almost won" rate. RTP-neutral: the mode only
-- rearranges losing grids, never tier counts or payout.
--
-- ticket_book.metadata_visibility records how much of a book's depletion state the operator reveals to
-- players (NONE = counts only; PARTIAL = also percent dispensed; FULL = also prize value dispensed,
-- estimated value left, and win frequency). Purely a data/UI concern — it changes no per-ticket odds;
-- the pool was fixed at print time.
--
-- Both NOT NULL with a backfill default matching the domain defaults (CLEAN / PARTIAL) for any
-- pre-existing rows. ddl-auto=validate: the entity columns must match these exactly.
ALTER TABLE game ADD COLUMN near_miss_mode VARCHAR(20) NOT NULL DEFAULT 'CLEAN';

ALTER TABLE ticket_book ADD COLUMN metadata_visibility VARCHAR(20) NOT NULL DEFAULT 'PARTIAL';
