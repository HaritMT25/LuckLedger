-- Persist the full verification report (the individual checks), not just the pass/fail flag, so the
-- GET /api/games/{id}/verification endpoint can be served from the database after the in-memory
-- GameStore is retired. NOT NULL with a backfill default for any pre-existing rows.
ALTER TABLE game ADD COLUMN verification_report JSONB NOT NULL DEFAULT '{"passed":true,"checks":[]}'::jsonb;
