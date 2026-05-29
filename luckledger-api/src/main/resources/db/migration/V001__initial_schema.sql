-- LuckLedger initial schema.
-- Conventions: UUID primary keys, NUMERIC(19,4) for all money, TIMESTAMPTZ for time,
-- JSONB for ticket grids and report blobs.

CREATE TABLE player (
    id              UUID PRIMARY KEY,
    display_name    VARCHAR(120)  NOT NULL,
    coin_balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_borrowed  NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_spent     NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_won       NUMERIC(19,4) NOT NULL DEFAULT 0,
    ticket_count    INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE game (
    id                  UUID PRIMARY KEY,
    mechanic_type       VARCHAR(40)   NOT NULL,
    theme_id            VARCHAR(120)  NOT NULL,
    ticket_price        NUMERIC(19,4) NOT NULL,
    total_tickets       INTEGER       NOT NULL,
    payout_ratio        NUMERIC(19,4) NOT NULL,
    book_count          INTEGER       NOT NULL,
    dealer_count        INTEGER       NOT NULL,
    verification_passed BOOLEAN       NOT NULL,
    generation_time_ms  BIGINT        NOT NULL,
    near_miss           JSONB         NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE dealer (
    id              UUID PRIMARY KEY,
    game_id         UUID         NOT NULL REFERENCES game(id),
    name            VARCHAR(120) NOT NULL,
    tier            VARCHAR(20)  NOT NULL,
    rank_score      INTEGER      NOT NULL,
    books_per_cycle INTEGER      NOT NULL,
    books_depleted  INTEGER      NOT NULL
);
CREATE INDEX idx_dealer_game ON dealer(game_id);

CREATE TABLE ticket_book (
    id               UUID PRIMARY KEY,
    game_id          UUID    NOT NULL REFERENCES game(id),
    dealer_id        UUID    REFERENCES dealer(id),
    pool_contract_id UUID    NOT NULL,
    total_tickets    INTEGER NOT NULL,
    next_index       INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_book_game ON ticket_book(game_id);
CREATE INDEX idx_book_dealer ON ticket_book(dealer_id);

CREATE TABLE ticket (
    id                 UUID PRIMARY KEY,
    book_id            UUID          REFERENCES ticket_book(id),
    game_id            UUID          NOT NULL REFERENCES game(id),
    outcome_id         UUID          NOT NULL,
    mechanic_type      VARCHAR(40)   NOT NULL,
    prize_amount       NUMERIC(19,4) NOT NULL,
    position_in_book   INTEGER,
    status             VARCHAR(20)   NOT NULL,
    grid               JSONB         NOT NULL,
    skinned_grid       JSONB         NOT NULL,
    revealed           BOOLEAN       NOT NULL DEFAULT FALSE,
    revealed_is_winner BOOLEAN,
    revealed_prize     NUMERIC(19,4)
);
CREATE INDEX idx_ticket_book ON ticket(book_id);
CREATE INDEX idx_ticket_game ON ticket(game_id);

CREATE TABLE ledger_transaction (
    id         UUID PRIMARY KEY,
    player_id  UUID          NOT NULL REFERENCES player(id),
    type       VARCHAR(20)   NOT NULL,
    amount     NUMERIC(19,4) NOT NULL,
    dealer_id  UUID,
    book_id    UUID,
    ticket_id  UUID,
    created_at TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_txn_player ON ledger_transaction(player_id);
CREATE INDEX idx_txn_player_type ON ledger_transaction(player_id, type);
