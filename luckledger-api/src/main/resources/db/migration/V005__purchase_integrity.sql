-- Purchase integrity backstop. A book is sold strictly sequentially (position 0, 1, 2, ...); two
-- concurrent buyers must never be handed the same slot. Application-level pessimistic locks now
-- serialize the sale (see PurchaseGateway), and this partial unique index is the database's own
-- guarantee: at most one ticket per (book, position). It doubles as the composite lookup index the
-- next-ticket draw relies on. Partial (position_in_book IS NOT NULL) because the column is nullable.
-- ddl-auto=validate ignores indexes, so no entity change is needed.
CREATE UNIQUE INDEX idx_ticket_book_position ON ticket(book_id, position_in_book)
    WHERE position_in_book IS NOT NULL;
