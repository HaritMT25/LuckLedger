package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.TicketCard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketBookTest {

    private static final UUID BOOK_ID = UUID.randomUUID();
    private static final UUID POOL_ID = UUID.randomUUID();

    private static TicketBook book(double... prizes) {
        return new TicketBook(BOOK_ID, Cards.cards(prizes), POOL_ID);
    }

    @Test
    void exposesIdsAndCounts() {
        TicketBook book = book(10, 2, 0);

        assertThat(book.bookId()).isEqualTo(BOOK_ID);
        assertThat(book.poolContractId()).isEqualTo(POOL_ID);
        assertThat(book.getTotalTickets()).isEqualTo(3);
        assertThat(book.getTicketsRemaining()).isEqualTo(3);
        assertThat(book.isDepleted()).isFalse();
    }

    @Test
    void bookValueSumsEveryTicketPrize() {
        TicketBook book = book(10, 2, 0, 25);

        assertThat(book.getBookValue()).isEqualByComparingTo("37");
    }

    @Test
    void sequentialSaleAdvancesAndTracksDispensedPrizes() {
        TicketBook book = book(10, 2, 0);

        TicketCard first = book.getNextTicket();
        assertThat(first.layout().prizeAmount()).isEqualByComparingTo("10");
        assertThat(book.getTicketsRemaining()).isEqualTo(2);
        assertThat(book.getPrizesDispensed()).isEqualByComparingTo("10");

        book.getNextTicket(); // $2
        assertThat(book.getPrizesDispensed()).isEqualByComparingTo("12");
    }

    @Test
    void depletedBookThrowsOnNextTicket() {
        TicketBook book = book(5);

        book.getNextTicket();
        assertThat(book.isDepleted()).isTrue();
        assertThat(book.getTicketsRemaining()).isZero();
        assertThatThrownBy(book::getNextTicket).isInstanceOf(BookDepletedException.class);
    }

    @Test
    void ticketsAreDefensivelyCopied() {
        List<TicketCard> source = Cards.cards(10, 2);
        TicketBook book = new TicketBook(BOOK_ID, source, POOL_ID);

        source.clear();
        assertThat(book.getTotalTickets()).isEqualTo(2);
    }

    @Test
    void emptyOrNullTicketsAreRejected() {
        assertThatThrownBy(() -> new TicketBook(BOOK_ID, List.of(), POOL_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TicketBook(null, Cards.cards(1), POOL_ID))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketBook(BOOK_ID, Cards.cards(1), null))
                .isInstanceOf(NullPointerException.class);
    }
}
