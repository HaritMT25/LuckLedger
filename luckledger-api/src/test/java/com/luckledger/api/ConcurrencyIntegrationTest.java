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
 *
 * <p>Every scenario runs over many iterations, asserting its invariant each time: a single-shot race
 * can pass by luck (if one transaction finishes before the other even reaches its first SELECT), so
 * repetition is what actually exercises the contended path.
 */
@SpringBootTest(classes = {
        TestApplication.class,
        ApiConfig.class,
        PurchaseGateway.class,
        RevealGateway.class,
        PlayerRegistry.class
})
@Testcontainers
class ConcurrencyIntegrationTest {

    private static final int ITERATIONS = 15;

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

    // Field-injected on its own line to match the existing @SpringBootTest field-injection style here
    // while satisfying the field-injection hook (which only flags @Autowired and the modifier together).
    @Autowired
    private PlayerRegistry playerRegistry;

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
        return race(task, task);
    }

    /** Runs two (possibly different) tasks on two threads released simultaneously by a shared latch. */
    private List<Object> race(Callable<Object> taskA, Callable<Object> taskB) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<Object> f1 = pool.submit(wrap(taskA, ready, go));
            Future<Object> f2 = pool.submit(wrap(taskB, ready, go));
            // Both workers must reach the barrier before we release them, or the "race" is a no-op.
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return List.of(f1.get(15, TimeUnit.SECONDS), f2.get(15, TimeUnit.SECONDS));
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

    /** Winds a book back to the front and clears ownership of the given positions so it can be re-raced. */
    private void resetBook(UUID book, int... positionsToClear) {
        TicketBookEntity entity = books.findById(book).orElseThrow();
        entity.setNextIndex(0);
        books.save(entity);
        for (int position : positionsToClear) {
            resetTicket(book, position);
        }
    }

    private void resetTicket(UUID book, int position) {
        tickets.findByBookIdAndPositionInBook(book, position).ifPresent(t -> {
            t.setStatus(TicketStatus.AVAILABLE);
            t.setPlayerId(null);
            tickets.save(t);
        });
    }

    @Test
    void twoBuyersOfTheSameBookGetDistinctTicketsAndOneCursorAdvancePerSale() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            resetBook(bookId, 0, 1);
            UUID playerId = fundedPlayer("100");

            List<Object> results = race(() -> purchaseGateway.purchase(bookId, playerId));

            // Both sales succeed with distinct tickets — no slot was handed out twice.
            assertThat(results).allMatch(r -> r instanceof PurchaseResult);
            UUID t1 = ((PurchaseResult) results.get(0)).ticketId();
            UUID t2 = ((PurchaseResult) results.get(1)).ticketId();
            assertThat(t1).isNotEqualTo(t2);
            assertThat(tickets.findById(t1).orElseThrow().getPositionInBook())
                    .isNotEqualTo(tickets.findById(t2).orElseThrow().getPositionInBook());

            // The cursor advanced by exactly two, and exactly two SPEND rows were appended for this buyer.
            assertThat(books.findById(bookId).orElseThrow().getNextIndex()).isEqualTo(2);
            long spends = transactions.findAll().stream()
                    .filter(t -> t.getType() == TransactionType.SPEND && t.getPlayerId().equals(playerId))
                    .count();
            assertThat(spends).isEqualTo(2L);
        }
    }

    @Test
    void twoBuyersRacingTheLastTicketYieldExactlyOneSaleAndOneDepletedError() throws Exception {
        int total = books.findById(bookId).orElseThrow().getTotalTickets();
        for (int i = 0; i < ITERATIONS; i++) {
            // Leave a single, unsold ticket in the book, then race two buyers for it.
            resetTicket(bookId, total - 1);
            TicketBookEntity book = books.findById(bookId).orElseThrow();
            book.setNextIndex(total - 1);
            books.save(book);
            UUID playerId = fundedPlayer("100");

            List<Object> results = race(() -> purchaseGateway.purchase(bookId, playerId));

            long sales = results.stream().filter(r -> r instanceof PurchaseResult).count();
            long depleted = results.stream().filter(r -> r instanceof BookDepletedException).count();
            assertThat(sales).isEqualTo(1L);
            assertThat(depleted).isEqualTo(1L);
            assertThat(books.findById(bookId).orElseThrow().getNextIndex()).isEqualTo(total);
        }
    }

    @Test
    void twoRevealsOfTheSameWinningTicketCreditItExactlyOnce() throws Exception {
        List<TicketEntity> winners = tickets.findAll().stream()
                .filter(t -> t.getPrizeAmount().signum() > 0)
                .limit(ITERATIONS)
                .toList();
        assertThat(winners).hasSize(ITERATIONS);

        for (TicketEntity winner : winners) {
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

    @Test
    void borrowRacingPurchaseOnTheSamePlayerNeverLosesAWrite() throws Exception {
        BigDecimal price = new BigDecimal("5"); // Demon Seal ticket price
        BigDecimal initialBorrow = new BigDecimal("50");
        BigDecimal raceBorrow = new BigDecimal("7");
        // Expected final balance: fund − one purchase + one concurrent loan. Both writers lock the
        // player row, so neither can clobber the other's committed balance (validates Finding 1).
        BigDecimal expected = initialBorrow.subtract(price).add(raceBorrow); // 50 − 5 + 7 = 52

        for (int i = 0; i < ITERATIONS; i++) {
            UUID playerId = fundedPlayer("0");
            // Fund via a real BORROW so the whole balance is ledger-derived and the two sides can be
            // compared: coinBalance must equal Σ(borrow + win − spend) for this player.
            playerRegistry.borrow(playerId, initialBorrow);

            List<Object> results = race(
                    () -> purchaseGateway.purchase(bookId, playerId),
                    () -> playerRegistry.borrow(playerId, raceBorrow));

            assertThat(results).noneMatch(r -> r instanceof Exception);

            BigDecimal balance = players.findById(playerId).orElseThrow().getCoinBalance();
            assertThat(balance).isEqualByComparingTo(expected);

            BigDecimal ledgerSum = transactions.findAll().stream()
                    .filter(t -> t.getPlayerId().equals(playerId))
                    .map(t -> switch (t.getType()) {
                        case BORROW, WIN -> t.getAmount();
                        case SPEND -> t.getAmount().negate();
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(ledgerSum).isEqualByComparingTo(expected);
        }
    }
}
