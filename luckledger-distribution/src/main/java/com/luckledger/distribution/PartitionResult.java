package com.luckledger.distribution;

import java.util.List;
import java.util.Objects;

/**
 * The result of partitioning a generated batch into books: the books themselves and the
 * {@link BookValueStats} characterizing their value spread.
 *
 * @param books the partitioned books, in order; non-null, non-empty, no null elements (copied)
 * @param bookValueStats the value-spread summary across {@code books}; never {@code null}
 */
public record PartitionResult(List<TicketBook> books, BookValueStats bookValueStats) {

    public PartitionResult {
        Objects.requireNonNull(books, "books must not be null");
        Objects.requireNonNull(bookValueStats, "bookValueStats must not be null");
        if (books.isEmpty()) {
            throw new IllegalArgumentException("books must not be empty");
        }
        books.forEach(b -> Objects.requireNonNull(b, "books must not contain null elements"));
        books = List.copyOf(books);
    }
}
