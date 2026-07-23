-- Commit-reveal proof that a ticket's outcome existed before it was scratched: each ticket carries a
-- SHA-256 grid_commitment (public from purchase) and a commitment_salt revealed only on scratch, so
-- anyone can re-hash the revealed grid + salt and confirm it equals the commitment fixed at generation.
-- Both nullable so pre-existing (legacy) ticket rows, which predate the scheme, remain valid.
ALTER TABLE ticket ADD COLUMN grid_commitment VARCHAR(64);
ALTER TABLE ticket ADD COLUMN commitment_salt VARCHAR(32);
