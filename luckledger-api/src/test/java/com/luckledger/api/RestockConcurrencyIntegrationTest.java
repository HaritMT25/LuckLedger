package com.luckledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.api.RestockService.RestockResult;
import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.GamePersistenceMapper;
import com.luckledger.api.persistence.GamePersistenceMapper.PersistedGame;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.PlayerRepository;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.api.persistence.TransactionRepository;
import com.luckledger.distribution.GameSetupResult;
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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the restock game-row write lock (see {@link RestockService} / {@link GameRepository#findByIdForUpdate})
 * serializes two concurrent restocks of the same game. Deliberately <strong>not</strong> {@code @Transactional}:
 * each racing restock must commit in its own transaction for the lock to matter, so the test seeds and tears
 * down the database by hand.
 *
 * <p>The invariant, asserted over several iterations: two restocks that both complete add exactly two
 * batches — the persisted book/ticket rows grow by {@code 2 × booksPerCycle} / {@code 2 × ticketsPerCycle},
 * and the game's folded-in totals ({@code book_count}, {@code total_tickets}) match the rows actually
 * persisted. Without the lock both calls read the same starting totals and one {@code recordRestock} write
 * clobbers the other, drifting the recorded totals below the real row counts.
 */
@SpringBootTest(classes = {
        TestApplication.class,
        ApiConfig.class,
        GameStore.class,
        RestockService.class
})
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RestockConcurrencyIntegrationTest {

    private static final int ITERATIONS = 5;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private final RestockService restockService;
    private final GameRepository games;
    private final DealerRepository dealers;
    private final TicketBookRepository books;
    private final TicketRepository tickets;
    private final PlayerRepository players;
    private final TransactionRepository transactions;

    // Constructor injection (SpringExtension resolves the params) — the project bans field injection.
    RestockConcurrencyIntegrationTest(RestockService restockService, GameRepository games,
            DealerRepository dealers, TicketBookRepository books, TicketRepository tickets,
            PlayerRepository players, TransactionRepository transactions) {
        this.restockService = restockService;
        this.games = games;
        this.dealers = dealers;
        this.books = books;
        this.tickets = tickets;
        this.players = players;
        this.transactions = transactions;
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

    /** Seeds a fresh Demon Seal game stocked by its dealers and returns its id. */
    private UUID seedGame() {
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
        return gameId;
    }

    @Test
    void twoConcurrentRestocksOfTheSameGameAddExactlyTwoBatchesAndKeepTotalsConsistent() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            UUID gameId = seedGame();
            GameEntity before = games.findById(gameId).orElseThrow();
            int booksBefore = books.findByGameId(gameId).size();
            long ticketsBefore = tickets.count();
            int recordedBooksBefore = before.getBookCount();
            int recordedTicketsBefore = before.getTotalTickets();

            List<Object> results = race(() -> restockService.restock(gameId));

            // Both restocks completed — neither threw, and each reports the same single-cycle batch size.
            assertThat(results).allMatch(r -> r instanceof RestockResult);
            RestockResult first = (RestockResult) results.get(0);
            RestockResult second = (RestockResult) results.get(1);
            int booksPerCycle = first.booksAdded();
            int ticketsPerCycle = first.ticketsAdded();
            assertThat(booksPerCycle).isGreaterThan(0);
            assertThat(second.booksAdded()).isEqualTo(booksPerCycle);
            assertThat(second.ticketsAdded()).isEqualTo(ticketsPerCycle);

            // Persisted rows grew by exactly two batches — no interleaving lost or duplicated a batch.
            assertThat(books.findByGameId(gameId)).hasSize(booksBefore + 2 * booksPerCycle);
            assertThat(tickets.count()).isEqualTo(ticketsBefore + 2L * ticketsPerCycle);

            // The game's folded-in totals reflect BOTH cycles — no lost update drifted them below the rows.
            GameEntity after = games.findById(gameId).orElseThrow();
            assertThat(after.getBookCount()).isEqualTo(recordedBooksBefore + 2 * booksPerCycle);
            assertThat(after.getTotalTickets()).isEqualTo(recordedTicketsBefore + 2 * ticketsPerCycle);
            assertThat(after.getBookCount()).isEqualTo(books.findByGameId(gameId).size());

            cleanUp();
        }
    }

    /** Runs the same task on two threads released simultaneously; returns each result or thrown exception. */
    private List<Object> race(Callable<Object> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<Object> f1 = pool.submit(wrap(task, ready, go));
            Future<Object> f2 = pool.submit(wrap(task, ready, go));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private static Callable<Object> wrap(Callable<Object> task, CountDownLatch ready, CountDownLatch go) {
        return () -> {
            ready.countDown();
            go.await();
            try {
                return task.call();
            } catch (Exception e) {
                return e;
            }
        };
    }
}
