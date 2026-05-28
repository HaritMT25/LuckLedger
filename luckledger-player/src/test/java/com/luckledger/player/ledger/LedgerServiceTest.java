package com.luckledger.player.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.ledger.BookStats;
import com.luckledger.domain.ledger.CurvePoint;
import com.luckledger.domain.ledger.DealerStats;
import com.luckledger.domain.ledger.InevitabilityCurveInsight;
import com.luckledger.domain.ledger.Insight;
import com.luckledger.domain.ledger.InsightGenerator;
import com.luckledger.domain.ledger.InsightSeverity;
import com.luckledger.domain.ledger.LedgerSnapshot;
import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LedgerService} (Bead LuckLedger-7hs.19). Organised around the four
 * read-side responsibilities of Subsystem 11: snapshot folding of pre-computed totals,
 * insight aggregation over registered generators, per-dealer comparison, and the
 * inevitability curve. Backed by a real {@link TransactionRecorder} rather than a mock, so the
 * fold-from-history behaviour is exercised end to end.
 */
class LedgerServiceTest {

    private static final Instant BASE = Instant.parse("2026-05-28T00:00:00Z");

    private final TransactionRecorder recorder = new TransactionRecorder();
    private final AtomicInteger seq = new AtomicInteger();
    private final Map<String, UUID> ticketIds = new java.util.HashMap<>();

    private LedgerService serviceWith(InsightGenerator... generators) {
        return new LedgerService(recorder, List.of(generators));
    }

    // ---- construction ----

    @Nested
    class Construction {

        @Test
        void rejectsNullRecorder() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new LedgerService(null, List.of()))
                    .withMessageContaining("recorder");
        }

        @Test
        void rejectsNullGeneratorList() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new LedgerService(recorder, null))
                    .withMessageContaining("insightGenerators");
        }

        @Test
        void copiesGeneratorListDefensivelySoLaterMutationIsIgnored() {
            List<InsightGenerator> mutable = new ArrayList<>();
            LedgerService service = new LedgerService(recorder, mutable);
            mutable.add(snapshot -> Optional.of(anyInsight()));

            UUID player = UUID.randomUUID();
            recorder.record(spend(player, dealer(), book(), "1", "10"));

            assertThat(service.generateInsights(player)).isEmpty();
        }
    }

    // ---- getSnapshot: reads pre-computed totals ----

    @Nested
    class GetSnapshot {

        @Test
        void rejectsNullPlayerId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> serviceWith().getSnapshot(null))
                    .withMessageContaining("playerId");
        }

        @Test
        void unknownPlayerIsFullyZeroed() {
            LedgerSnapshot snapshot = serviceWith().getSnapshot(UUID.randomUUID());

            assertThat(snapshot.totalBorrowed()).isEqualByComparingTo("0");
            assertThat(snapshot.totalSpent()).isEqualByComparingTo("0");
            assertThat(snapshot.totalWon()).isEqualByComparingTo("0");
            assertThat(snapshot.netPosition()).isEqualByComparingTo("0");
            assertThat(snapshot.ticketCount()).isZero();
            assertThat(snapshot.rollingReturnRate()).isEqualByComparingTo("0");
            assertThat(snapshot.perDealerStats()).isEmpty();
            assertThat(snapshot.perBookStats()).isEmpty();
            assertThat(snapshot.recentTransactions()).isEmpty();
            assertThat(snapshot.sessionBorrowEvents()).isEmpty();
        }

        @Test
        void foldsTotalsByTransactionType() {
            UUID player = UUID.randomUUID();
            UUID dealer = dealer();
            UUID book = book();
            recorder.record(borrow(player, "500"));
            recorder.record(spend(player, dealer, book, "t1", "10"));
            recorder.record(win(player, dealer, book, "t1", "15"));
            recorder.record(spend(player, dealer, book, "t2", "10")); // loser, no WIN

            LedgerSnapshot snapshot = serviceWith().getSnapshot(player);

            assertThat(snapshot.totalBorrowed()).isEqualByComparingTo("500");
            assertThat(snapshot.totalSpent()).isEqualByComparingTo("20");
            assertThat(snapshot.totalWon()).isEqualByComparingTo("15");
            assertThat(snapshot.ticketCount()).isEqualTo(2);
        }

        @Test
        void netPositionIsWonMinusSpentAndMayBeNegative() {
            UUID player = UUID.randomUUID();
            recorder.record(spend(player, dealer(), book(), "t1", "100"));
            recorder.record(win(player, dealer(), book(), "t1", "40"));

            assertThat(serviceWith().getSnapshot(player).netPosition()).isEqualByComparingTo("-60");
        }

        @Test
        void rollingReturnRateIsWonOverSpent() {
            UUID player = UUID.randomUUID();
            recorder.record(spend(player, dealer(), book(), "t1", "100"));
            recorder.record(win(player, dealer(), book(), "t1", "70"));

            assertThat(serviceWith().getSnapshot(player).rollingReturnRate())
                    .isEqualByComparingTo("0.70");
        }

        @Test
        void rollingReturnRateIsZeroWhenNothingSpent() {
            UUID player = UUID.randomUUID();
            recorder.record(borrow(player, "1000"));

            assertThat(serviceWith().getSnapshot(player).rollingReturnRate()).isEqualByComparingTo("0");
        }

        @Test
        void borrowsAreExcludedFromSpendAndTicketCount() {
            UUID player = UUID.randomUUID();
            recorder.record(borrow(player, "300"));
            recorder.record(borrow(player, "200"));

            LedgerSnapshot snapshot = serviceWith().getSnapshot(player);

            assertThat(snapshot.totalBorrowed()).isEqualByComparingTo("500");
            assertThat(snapshot.totalSpent()).isEqualByComparingTo("0");
            assertThat(snapshot.ticketCount()).isZero();
        }

        @Test
        void recentTransactionsArePopulatedNewestFirst() {
            UUID player = UUID.randomUUID();
            recorder.record(borrow(player, "100"));
            recorder.record(spend(player, dealer(), book(), "t1", "10"));

            List<Transaction> recent = serviceWith().getSnapshot(player).recentTransactions();

            assertThat(recent).hasSize(2);
            assertThat(recent.get(0).type()).isEqualTo(TransactionType.SPEND);
            assertThat(recent.get(1).type()).isEqualTo(TransactionType.BORROW);
        }

        @Test
        void sessionBorrowEventsContainOnlyBorrows() {
            UUID player = UUID.randomUUID();
            recorder.record(borrow(player, "100"));
            recorder.record(spend(player, dealer(), book(), "t1", "10"));
            recorder.record(borrow(player, "200"));

            assertThat(serviceWith().getSnapshot(player).sessionBorrowEvents())
                    .hasSize(2)
                    .allMatch(tx -> tx.type() == TransactionType.BORROW);
        }

        @Test
        void snapshotIsIsolatedPerPlayer() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            recorder.record(spend(alice, dealer(), book(), "a", "10"));
            recorder.record(spend(bob, dealer(), book(), "b", "99"));

            assertThat(serviceWith().getSnapshot(alice).totalSpent()).isEqualByComparingTo("10");
            assertThat(serviceWith().getSnapshot(bob).totalSpent()).isEqualByComparingTo("99");
        }
    }

    // ---- per-dealer / per-book drill-down ----

    @Nested
    class PerDealerAndBookStats {

        @Test
        void perDealerStatsGroupSpendAndWinByDealer() {
            UUID player = UUID.randomUUID();
            UUID joes = dealer();
            UUID downtown = dealer();
            recorder.record(spend(player, joes, book(), "a", "100"));
            recorder.record(win(player, joes, book(), "a", "72"));
            recorder.record(spend(player, downtown, book(), "b", "100"));
            recorder.record(win(player, downtown, book(), "b", "58"));

            Map<UUID, DealerStats> stats = serviceWith().getSnapshot(player).perDealerStats();

            assertThat(stats).containsOnlyKeys(joes, downtown);
            DealerStats joeStats = stats.get(joes);
            assertThat(joeStats.dealerId()).isEqualTo(joes);
            assertThat(joeStats.ticketsBought()).isEqualTo(1);
            assertThat(joeStats.totalSpent()).isEqualByComparingTo("100");
            assertThat(joeStats.totalWon()).isEqualByComparingTo("72");
            assertThat(joeStats.returnRate()).isEqualByComparingTo("0.72");
            assertThat(joeStats.dealerName()).isNotBlank();
            assertThat(stats.get(downtown).returnRate()).isEqualByComparingTo("0.58");
        }

        @Test
        void perBookStatsGroupSpendAndWinByBook() {
            UUID player = UUID.randomUUID();
            UUID dealer = dealer();
            UUID book1 = book();
            UUID book2 = book();
            recorder.record(spend(player, dealer, book1, "a", "50"));
            recorder.record(spend(player, dealer, book1, "b", "50"));
            recorder.record(win(player, dealer, book1, "b", "10"));
            recorder.record(spend(player, dealer, book2, "c", "50")); // loser

            Map<UUID, BookStats> stats = serviceWith().getSnapshot(player).perBookStats();

            assertThat(stats).containsOnlyKeys(book1, book2);
            assertThat(stats.get(book1).ticketsBought()).isEqualTo(2);
            assertThat(stats.get(book1).totalSpent()).isEqualByComparingTo("100");
            assertThat(stats.get(book1).totalWon()).isEqualByComparingTo("10");
            assertThat(stats.get(book2).totalWon()).isEqualByComparingTo("0");
            assertThat(stats.get(book2).returnRate()).isEqualByComparingTo("0");
        }

        @Test
        void snapshotMapsAreUnmodifiable() {
            UUID player = UUID.randomUUID();
            recorder.record(spend(player, dealer(), book(), "a", "10"));

            Map<UUID, DealerStats> stats = serviceWith().getSnapshot(player).perDealerStats();

            assertThatThrownBy(() -> stats.put(UUID.randomUUID(), null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ---- generateInsights: aggregates the non-empty results ----

    @Nested
    class GenerateInsights {

        @Test
        void runsEveryGeneratorAndCollectsOnlyPresentResults() {
            UUID player = UUID.randomUUID();
            recorder.record(spend(player, dealer(), book(), "t1", "10"));

            Insight fired = anyInsight();
            AtomicInteger calls = new AtomicInteger();
            InsightGenerator firing = snapshot -> {
                calls.incrementAndGet();
                return Optional.of(fired);
            };
            InsightGenerator silent = snapshot -> {
                calls.incrementAndGet();
                return Optional.empty();
            };

            List<Insight> insights =
                    new LedgerService(recorder, List.of(firing, silent)).generateInsights(player);

            assertThat(calls).hasValue(2);
            assertThat(insights).containsExactly(fired);
        }

        @Test
        void preservesGeneratorRegistrationOrderForFiringGenerators() {
            UUID player = UUID.randomUUID();
            recorder.record(spend(player, dealer(), book(), "t1", "10"));

            Insight first = new Insight("FIRST", InsightSeverity.INFO, "1", "m1", Map.of(), BASE);
            Insight second = new Insight("SECOND", InsightSeverity.WARNING, "2", "m2", Map.of(), BASE);

            List<Insight> insights = new LedgerService(
                            recorder,
                            List.of(
                                    snapshot -> Optional.of(first),
                                    snapshot -> Optional.empty(),
                                    snapshot -> Optional.of(second)))
                    .generateInsights(player);

            assertThat(insights).containsExactly(first, second);
        }

        @Test
        void isEmptyWhenNoGeneratorFires() {
            UUID player = UUID.randomUUID();
            recorder.record(spend(player, dealer(), book(), "t1", "10"));

            assertThat(serviceWith(snapshot -> Optional.empty()).generateInsights(player)).isEmpty();
        }

        @Test
        void isEmptyWhenNoGeneratorsRegistered() {
            UUID player = UUID.randomUUID();
            recorder.record(spend(player, dealer(), book(), "t1", "10"));

            assertThat(serviceWith().generateInsights(player)).isEmpty();
        }

        @Test
        void passesAFreshlyComputedSnapshotToGenerators() {
            UUID player = UUID.randomUUID();
            recorder.record(spend(player, dealer(), book(), "t1", "100"));
            recorder.record(win(player, dealer(), book(), "t1", "40"));

            AtomicReference<LedgerSnapshot> seen = new AtomicReference<>();
            serviceWith(snapshot -> {
                        seen.set(snapshot);
                        return Optional.empty();
                    })
                    .generateInsights(player);

            assertThat(seen.get()).isNotNull();
            assertThat(seen.get().totalSpent()).isEqualByComparingTo("100");
            assertThat(seen.get().totalWon()).isEqualByComparingTo("40");
        }

        @Test
        void firesARealGeneratorOnAQualifyingHistory() {
            UUID player = UUID.randomUUID();
            UUID dealer = dealer();
            UUID book = book();
            // 25 tickets at a net loss: spends 250, wins 50 -> netPosition -200, ticketCount 25.
            for (int i = 0; i < 25; i++) {
                recorder.record(spend(player, dealer, book, "t" + i, "10"));
            }
            recorder.record(win(player, dealer, book, "t0", "50"));

            LedgerService service = new LedgerService(
                    recorder, List.of(new InevitabilityCurveInsight(fixedClock())));

            List<Insight> insights = service.generateInsights(player);

            assertThat(insights).hasSize(1);
            assertThat(insights.get(0).type()).isEqualTo("INEVITABILITY_CURVE");
            assertThat(insights.get(0).severity()).isEqualTo(InsightSeverity.WARNING);
        }

        @Test
        void realGeneratorStaysSilentBelowItsThreshold() {
            UUID player = UUID.randomUUID();
            recorder.record(spend(player, dealer(), book(), "t1", "10")); // only one ticket

            LedgerService service = new LedgerService(
                    recorder, List.of(new InevitabilityCurveInsight(fixedClock())));

            assertThat(service.generateInsights(player)).isEmpty();
        }
    }

    // ---- getDealerComparison ----

    @Nested
    class GetDealerComparison {

        @Test
        void rejectsNullPlayerId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> serviceWith().getDealerComparison(null))
                    .withMessageContaining("playerId");
        }

        @Test
        void returnsPerDealerStats() {
            UUID player = UUID.randomUUID();
            UUID dealer = dealer();
            recorder.record(spend(player, dealer, book(), "a", "100"));
            recorder.record(win(player, dealer, book(), "a", "65"));

            Map<UUID, DealerStats> comparison = serviceWith().getDealerComparison(player);

            assertThat(comparison).containsOnlyKeys(dealer);
            assertThat(comparison.get(dealer).returnRate()).isEqualByComparingTo("0.65");
        }

        @Test
        void isEmptyForAPlayerWithNoPurchases() {
            UUID player = UUID.randomUUID();
            recorder.record(borrow(player, "100"));

            assertThat(serviceWith().getDealerComparison(player)).isEmpty();
        }
    }

    // ---- getInevitabilityCurve ----

    @Nested
    class GetInevitabilityCurve {

        @Test
        void rejectsNullPlayerId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> serviceWith().getInevitabilityCurve(null))
                    .withMessageContaining("playerId");
        }

        @Test
        void hasOnePointPerTicketWithCumulativeTotals() {
            UUID player = UUID.randomUUID();
            UUID dealer = dealer();
            UUID book = book();
            recorder.record(spend(player, dealer, book, "t1", "10"));
            recorder.record(win(player, dealer, book, "t1", "5"));
            recorder.record(spend(player, dealer, book, "t2", "10")); // loser
            recorder.record(spend(player, dealer, book, "t3", "10"));
            recorder.record(win(player, dealer, book, "t3", "30"));

            List<CurvePoint> curve = serviceWith().getInevitabilityCurve(player);

            assertThat(curve).hasSize(3);

            assertThat(curve.get(0).ticketNumber()).isEqualTo(1);
            assertThat(curve.get(0).cumulativeSpent()).isEqualByComparingTo("10");
            assertThat(curve.get(0).cumulativeWon()).isEqualByComparingTo("5");
            assertThat(curve.get(0).netPosition()).isEqualByComparingTo("-5");

            assertThat(curve.get(1).ticketNumber()).isEqualTo(2);
            assertThat(curve.get(1).cumulativeSpent()).isEqualByComparingTo("20");
            assertThat(curve.get(1).cumulativeWon()).isEqualByComparingTo("5");
            assertThat(curve.get(1).netPosition()).isEqualByComparingTo("-15");

            assertThat(curve.get(2).ticketNumber()).isEqualTo(3);
            assertThat(curve.get(2).cumulativeSpent()).isEqualByComparingTo("30");
            assertThat(curve.get(2).cumulativeWon()).isEqualByComparingTo("35");
            assertThat(curve.get(2).netPosition()).isEqualByComparingTo("5");
        }

        @Test
        void attributesAWinToTheTicketThatPaidForItEvenWhenRevealedLater() {
            UUID player = UUID.randomUUID();
            UUID dealer = dealer();
            UUID book = book();
            // Buy two tickets, then reveal the first one's win after the second purchase.
            recorder.record(spend(player, dealer, book, "t1", "10"));
            recorder.record(spend(player, dealer, book, "t2", "10"));
            recorder.record(win(player, dealer, book, "t1", "20"));

            List<CurvePoint> curve = serviceWith().getInevitabilityCurve(player);

            assertThat(curve).hasSize(2);
            assertThat(curve.get(0).cumulativeWon()).isEqualByComparingTo("20");
            assertThat(curve.get(0).netPosition()).isEqualByComparingTo("10");
            assertThat(curve.get(1).cumulativeWon()).isEqualByComparingTo("20");
            assertThat(curve.get(1).netPosition()).isEqualByComparingTo("0");
        }

        @Test
        void isEmptyWithoutPurchases() {
            UUID player = UUID.randomUUID();
            recorder.record(borrow(player, "100"));

            assertThat(serviceWith().getInevitabilityCurve(player)).isEmpty();
        }
    }

    // ---- transaction builders ----

    private Instant next() {
        return BASE.plusSeconds(seq.incrementAndGet());
    }

    private static Clock fixedClock() {
        return Clock.fixed(BASE, ZoneOffset.UTC);
    }

    private static UUID dealer() {
        return UUID.randomUUID();
    }

    private static UUID book() {
        return UUID.randomUUID();
    }

    private Transaction borrow(UUID player, String amount) {
        return new Transaction(
                UUID.randomUUID(), player, TransactionType.BORROW,
                new BigDecimal(amount), null, null, null, next());
    }

    private Transaction spend(UUID player, UUID dealer, UUID book, String ticket, String amount) {
        return new Transaction(
                UUID.randomUUID(), player, TransactionType.SPEND,
                new BigDecimal(amount), dealer, book, ticketId(ticket), next());
    }

    private Transaction win(UUID player, UUID dealer, UUID book, String ticket, String amount) {
        return new Transaction(
                UUID.randomUUID(), player, TransactionType.WIN,
                new BigDecimal(amount), dealer, book, ticketId(ticket), next());
    }

    private static Insight anyInsight() {
        return new Insight("TEST", InsightSeverity.INFO, "t", "m", Map.of(), BASE);
    }

    private UUID ticketId(String label) {
        return ticketIds.computeIfAbsent(label, k -> UUID.randomUUID());
    }
}
