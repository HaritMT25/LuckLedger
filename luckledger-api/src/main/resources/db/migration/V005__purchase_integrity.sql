-- Purchase integrity support. Tickets are pre-INSERTed at generation time with (book_id,
-- position_in_book) already populated, and a sale is only an UPDATE of that existing row — so this
-- unique index CANNOT fire on a double sale; it does not guard the sale race. What it does guard is
-- generation: at most one ticket may occupy a given (book, position), catching a duplicate-position
-- bug when a pool is built. It also doubles as the composite lookup index the next-ticket draw relies
-- on. Sale-race safety is provided entirely in the application: the pessimistic book lock serializes
-- the cursor advance, and the drawn ticket row is then loaded FOR UPDATE and checked "still unsold"
-- before it is written (see PurchaseGateway). Partial (position_in_book IS NOT NULL) because the
-- column is nullable. ddl-auto=validate ignores indexes, so no entity change is needed.
CREATE UNIQUE INDEX idx_ticket_book_position ON ticket(book_id, position_in_book)
    WHERE position_in_book IS NOT NULL;
