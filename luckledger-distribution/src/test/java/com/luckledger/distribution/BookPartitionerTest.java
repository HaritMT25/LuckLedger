package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.TicketCard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookPartitionerTest {

    private final BookPartitioner partitioner = new BookPartitioner();
    private static final UUID POOL_ID = UUID.randomUUID();

    @Test
    void dealsEveryTicketIntoExactlyOneBook() {
        List<TicketCard> tickets = Cards.cards(10, 2, 0, 5, 0, 1);

        PartitionResult result = partitioner.partition(tickets, 3, POOL_ID);

        assertThat(result.books()).hasSize(3);
        int total = result.books().stream().mapToInt(TicketBook::getTotalTickets).sum();
        assertThat(total).isEqualTo(6);
        assertThat(result.books()).allSatisfy(b -> assertThat(b.poolContractId()).isEqualTo(POOL_ID));
    }

    @Test
    void unevenCountsGiveTheEarlierBooksTheExtraTickets() {
        // 7 tickets into 3 books -> sizes 3, 2, 2
        PartitionResult result = partitioner.partition(Cards.cards(0, 0, 0, 0, 0, 0, 0), 3, POOL_ID);

        assertThat(result.books()).extracting(TicketBook::getTotalTickets).containsExactly(3, 2, 2);
    }

    @Test
    void preservesInputOrderWithinBooks() {
        // sequential chunks: first book is the first two tickets in order
        List<TicketCard> tickets = Cards.cards(10, 2, 0, 5);
        PartitionResult result = partitioner.partition(tickets, 2, POOL_ID);

        TicketBook first = result.books().get(0);
        assertThat(first.getNextTicket().layout().prizeAmount()).isEqualByComparingTo("10");
        assertThat(first.getNextTicket().layout().prizeAmount()).isEqualByComparingTo("2");
    }

    @Test
    void computesValueStatisticsAcrossBooks() {
        // 2 books: [10+10]=20 and [0+0]=0  -> min 0, max 20, mean 10, median 10, stddev 10
        PartitionResult result = partitioner.partition(Cards.cards(10, 10, 0, 0), 2, POOL_ID);

        BookValueStats stats = result.bookValueStats();
        assertThat(stats.min()).isEqualTo(0.0);
        assertThat(stats.max()).isEqualTo(20.0);
        assertThat(stats.mean()).isEqualTo(10.0);
        assertThat(stats.median()).isEqualTo(10.0);
        assertThat(stats.stddev()).isEqualTo(10.0);
    }

    @Test
    void singleBookHoldsEverythingWithZeroSpread() {
        PartitionResult result = partitioner.partition(Cards.cards(10, 2, 0), 1, POOL_ID);

        assertThat(result.books()).hasSize(1);
        assertThat(result.books().get(0).getTotalTickets()).isEqualTo(3);
        assertThat(result.bookValueStats().stddev()).isZero();
    }

    @Test
    void invalidArgumentsAreRejected() {
        List<TicketCard> tickets = Cards.cards(1, 2, 3);
        assertThatThrownBy(() -> partitioner.partition(tickets, 0, POOL_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> partitioner.partition(tickets, 4, POOL_ID)) // more books than tickets
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> partitioner.partition(List.of(), 1, POOL_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> partitioner.partition(tickets, 1, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> partitioner.partition(null, 1, POOL_ID))
                .isInstanceOf(NullPointerException.class);
    }
}
