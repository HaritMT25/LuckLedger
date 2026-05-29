package com.luckledger.scratchflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.distribution.BookDepletedException;
import com.luckledger.distribution.Dealer;
import com.luckledger.distribution.DealerTier;
import com.luckledger.distribution.TicketBook;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.domain.player.InsufficientBalanceException;
import com.luckledger.domain.player.Player;
import com.luckledger.domain.scratch.PurchaseResult;
import com.luckledger.domain.scratch.TicketStatus;
import com.luckledger.player.ledger.InMemoryTransactionRecorder;
import com.luckledger.player.ledger.TransactionRecorder;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketPurchaseServiceTest {

    private static final BigDecimal PRICE = new BigDecimal("5");

    private TransactionRecorder recorder;
    private TicketPurchaseService service;
    private Player player;
    private Dealer dealer;

    @BeforeEach
    void setUp() {
        recorder = new InMemoryTransactionRecorder();
        service = new TicketPurchaseService(recorder);
        player = new Player(UUID.randomUUID(), "Player");
        player.recordBorrow(new BigDecimal("100")); // fund with free coins
        dealer = new Dealer(UUID.randomUUID(), "Lucky's", DealerTier.TIER_1, 0, 5, 0);
    }

    @Test
    void debitsTheBuyerDrawsATicketAndRecordsASpend() {
        TicketBook book = Fixtures.book(3);
        dealer.addBook(book);

        PurchaseResult result = service.purchase(player, dealer, book, PRICE);

        assertThat(result.ticketStatus()).isEqualTo(TicketStatus.SOLD);
        assertThat(result.coinsDeducted()).isEqualByComparingTo("5");
        assertThat(result.dealerId()).isEqualTo(dealer.dealerId());
        assertThat(player.getCoinBalance()).isEqualByComparingTo("95");
        assertThat(book.getTicketsRemaining()).isEqualTo(2);
        assertThat(recorder.getTransactions(player.getPlayerId(), TransactionType.SPEND)).hasSize(1);
    }

    @Test
    void depletingTheLastTicketAdvancesTheDealerLifetimeCount() {
        TicketBook book = Fixtures.book(1);
        dealer.addBook(book);

        service.purchase(player, dealer, book, PRICE);

        assertThat(book.isDepleted()).isTrue();
        assertThat(dealer.booksDepleted()).isEqualTo(1);
        assertThat(dealer.activeBooks()).isEmpty();
    }

    @Test
    void anUnaffordablePurchaseDrawsNoTicketAndRecordsNothing() {
        Player broke = new Player(UUID.randomUUID(), "Broke");
        TicketBook book = Fixtures.book(3);

        assertThatThrownBy(() -> service.purchase(broke, dealer, book, PRICE))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(book.getTicketsRemaining()).isEqualTo(3); // no ticket drawn
        assertThat(recorder.getTransactions(broke.getPlayerId())).isEmpty();
    }

    @Test
    void aDepletedBookCannotBePurchasedFrom() {
        TicketBook book = Fixtures.book(1);
        service.purchase(player, dealer, book, PRICE); // depletes it

        assertThatThrownBy(() -> service.purchase(player, dealer, book, PRICE))
                .isInstanceOf(BookDepletedException.class);
    }

    @Test
    void invalidArgumentsAreRejected() {
        TicketBook book = Fixtures.book(1);
        assertThatThrownBy(() -> service.purchase(player, dealer, book, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.purchase(null, dealer, book, PRICE))
                .isInstanceOf(NullPointerException.class);
    }
}
