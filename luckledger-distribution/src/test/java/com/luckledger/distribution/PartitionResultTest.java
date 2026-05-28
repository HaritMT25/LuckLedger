package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartitionResultTest {

    private static final BookValueStats STATS = new BookValueStats(1.0, 1.0, 1.0, 0.0, 1.0);

    private static TicketBook book() {
        return new TicketBook(UUID.randomUUID(), Cards.cards(1), UUID.randomUUID());
    }

    @Test
    void holdsBooksAndStats() {
        PartitionResult result = new PartitionResult(List.of(book()), STATS);

        assertThat(result.books()).hasSize(1);
        assertThat(result.bookValueStats()).isSameAs(STATS);
    }

    @Test
    void booksAreAnUnmodifiableCopy() {
        List<TicketBook> source = new ArrayList<>(List.of(book()));
        PartitionResult result = new PartitionResult(source, STATS);

        source.clear();
        assertThat(result.books()).hasSize(1);
        assertThatThrownBy(() -> result.books().add(book()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyBooksOrNullStatsRejected() {
        assertThatThrownBy(() -> new PartitionResult(List.of(), STATS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartitionResult(List.of(book()), null))
                .isInstanceOf(NullPointerException.class);
    }
}
