package com.luckledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.api.persistence.GamePersistenceMapper;
import com.luckledger.api.persistence.GamePersistenceMapper.PersistedGame;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.PlayerEntity;
import com.luckledger.api.persistence.PlayerRepository;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.api.persistence.TransactionRepository;
import com.luckledger.distribution.BookDepletedException;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.domain.scratch.PurchaseResult;
import com.luckledger.domain.scratch.TicketStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the pessimistic-locking discipline in the gateways holds under real concurrent transactions.
 * Deliberately <strong>not</strong> {@code @Transactional}: each racing gateway call must commit in its
 * own transaction for the locks to matter, so this test seeds and tears down the database by hand.
 */
@SpringBootTest(classes = {
        TestApplication.class,
        ApiConfig.class,
        PurchaseGateway.class,
        RevealGateway.class
})
@Testcontainers
class ConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired private PurchaseGateway purchaseGateway;
    @Autowired private RevealGateway revealGateway;
    @Autowired private GameRepository games;
    @Autowired private DealerRepository dealers;
    @Autowired private TicketBookRepository books;
    @Autowired private TicketRepository tickets;
    @Autowired private PlayerRepository players;
    @Autowired private TransactionRepository transactions;

    private UUID bookId;

    @BeforeEach
    void seed() {
        UUID gameId = UUID.randomUUID();
        GameSetupResult setup = TestGames.demonGame();
        PersistedGame persisted =
                GamePersistenceMapper.toPersisted(gameId, ApiConfig.demonConfig(), setup, Instant.now());
        games.save(persisted.game());
        setup.dealers().forEach(d -> dealers.save(new DealerEntity(
                d.dealerId(), d.name(), "Owner", null, List.of(gameId),
                d.tier(), d.rankScore(), d.booksPerCycle(), d.booksDepleted())));
        books.saveAll(persisted.books());
        tickets.saveAll(persisted.tickets());

        bookId = persisted.books().stream()
                .filter(b -> b.getDealerId() != null)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @AfterEach
    void cleanUp() {
        transactions.deleteAll();
        tickets.deleteAll();
        books.deleteAll();
        dealers.deleteAll();
        games.deleteAll();
        players.deleteAll();
    }

    private UUID fundedPlayer(String balance) {
        UUID id = UUID.randomUUID();
        players.save(new PlayerEntity(
                id, "Player", new BigDecimal(balance), new BigDecimal(balance),
                BigDecimal.ZERO, BigDecimal.ZERO, 0, Instant.now()));
        return id;
    }

    /** Runs the same task on two threads released simultaneously; returns each result or thrown exception. */
    private List<Object> race(Callable<Object> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Callable<Object> wrapped = () -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return e;
                }
            };
            Future<Object> f1 = pool.submit(wrapped);
            Future<Object> f2 = pool.submit(wrapped);
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            return List.of(f1.get(15, TimeUnit.SECONDS), f2.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoBuyersOfTheSameBookGetDistinctTicketsAndOneCursorAdvancePerSale() throws Exception {
        UUID playerId = fundedPlayer("100");

        List<Object> results = race(() -> purchaseGateway.purchase(bookId, playerId));

        // Both sales succeed with distinct tickets — no slot was handed out twice.
        assertThat(results).allMatch(r -> r instanceof PurchaseResult);
        UUID t1 = ((PurchaseResult) results.get(0)).ticketId();
        UUID t2 = ((PurchaseResult) results.get(1)).ticketId();
        assertThat(t1).isNotEqualTo(t2);
        assertThat(tickets.findById(t1).orElseThrow().getPositionInBook())
                .isNotEqualTo(tickets.findById(t2).orElseThrow().getPositionInBook());

        // The cursor advanced by exactly two, and exactly two SPEND rows were appended.
        assertThat(books.findById(bookId).orElseThrow().getNextIndex()).isEqualTo(2);
        long spends = transactions.findAll().stream()
                .filter(t -> t.getType() == TransactionType.SPEND && t.getPlayerId().equals(playerId))
                .count();
        assertThat(spends).isEqualTo(2L);
    }

    @Test
    void twoBuyersRacingTheLastTicketYieldExactlyOneSaleAndOneDepletedError() throws Exception {
        // Leave a single ticket in the book, then race two buyers for it.
        TicketBookEntity book = books.findById(bookId).orElseThrow();
        book.setNextIndex(book.getTotalTickets() - 1);
        books.save(book);
        UUID playerId = fundedPlayer("100");

        List<Object> results = race(() -> purchaseGateway.purchase(bookId, playerId));

        long sales = results.stream().filter(r -> r instanceof PurchaseResult).count();
        long depleted = results.stream().filter(r -> r instanceof BookDepletedException).count();
        assertThat(sales).isEqualTo(1L);
        assertThat(depleted).isEqualTo(1L);
        assertThat(books.findById(bookId).orElseThrow().getNextIndex())
                .isEqualTo(book.getTotalTickets());
    }

    @Test
    void twoRevealsOfTheSameWinningTicketCreditItExactlyOnce() throws Exception {
        TicketEntity winner = tickets.findAll().stream()
                .filter(t -> bookId.equals(t.getBookId()))
                .filter(t -> t.getPrizeAmount().signum() > 0)
                .findFirst()
                .orElseThrow();
        BigDecimal prize = winner.getPrizeAmount();

        UUID playerId = fundedPlayer("0");
        winner.setStatus(TicketStatus.SOLD);
        winner.setPlayerId(playerId);
        tickets.save(winner);
        UUID ticketId = winner.getId();

        List<Object> results = race(() -> revealGateway.reveal(ticketId, playerId));

        // Neither call errors, but the prize is credited — and recorded — exactly once.
        assertThat(results).noneMatch(r -> r instanceof Exception);
        long wins = transactions.findAll().stream()
                .filter(t -> t.getType() == TransactionType.WIN && t.getPlayerId().equals(playerId))
                .count();
        assertThat(wins).isEqualTo(1L);
        assertThat(players.findById(playerId).orElseThrow().getCoinBalance())
                .isEqualByComparingTo(prize);
    }
}
