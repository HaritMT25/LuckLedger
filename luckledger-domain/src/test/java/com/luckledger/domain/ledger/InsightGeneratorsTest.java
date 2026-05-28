package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Behavioural suite for every {@link InsightGenerator}: each generator must fire exactly when its
 * documented trigger condition holds and stay silent otherwise. Trigger thresholds are exercised at
 * their boundaries so an off-by-one drift in any generator is caught.
 */
class InsightGeneratorsTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    /**
     * A benign snapshot that crosses none of the six trigger conditions. Every generator must stay
     * silent against it; individual tests override only the fields relevant to their trigger.
     */
    @Test
    void allGenerators_stayingSilent_onBenignSnapshot() {
        LedgerSnapshot benign = builder().build();
        List<InsightGenerator> generators = List.of(
                new LossRateInsight(CLOCK),
                new LossChasingInsight(CLOCK),
                new LuckyStoreDebunkInsight(CLOCK),
                new VarianceExplanationInsight(CLOCK),
                new NearMissInsight(CLOCK),
                new InevitabilityCurveInsight(CLOCK));

        assertThat(generators)
                .allSatisfy(generator -> assertThat(generator.evaluate(benign)).isEmpty());
    }

    @Nested
    class LossRate {

        private final LossRateInsight generator = new LossRateInsight(CLOCK);

        @Test
        void fires_whenReturnRateBelowThreshold_andEnoughTickets() {
            LedgerSnapshot snapshot = builder()
                    .totalSpent("1000")
                    .totalWon("500")
                    .netPosition("-500")
                    .ticketCount(10)
                    .rollingReturnRate("0.50")
                    .build();

            Optional<Insight> result = generator.evaluate(snapshot);

            assertThat(result).isPresent();
            Insight insight = result.get();
            assertThat(insight.type()).isEqualTo("LOSS_RATE");
            assertThat(insight.severity()).isEqualTo(InsightSeverity.WARNING);
            assertThat(insight.message()).contains("50%");
            assertThat(insight.timestamp()).isEqualTo(FIXED_INSTANT);
        }

        @Test
        void silent_whenReturnRateExactlyAtThreshold() {
            LedgerSnapshot snapshot = builder()
                    .totalSpent("1000")
                    .totalWon("700")
                    .netPosition("-300")
                    .ticketCount(10)
                    .rollingReturnRate("0.70")
                    .build();

            assertThat(generator.evaluate(snapshot)).isEmpty();
        }

        @Test
        void silent_whenBelowTicketMinimum() {
            LedgerSnapshot snapshot = builder()
                    .totalSpent("1000")
                    .totalWon("500")
                    .netPosition("-500")
                    .ticketCount(9)
                    .rollingReturnRate("0.50")
                    .build();

            assertThat(generator.evaluate(snapshot)).isEmpty();
        }

        @Test
        void silent_whenNothingSpent() {
            LedgerSnapshot snapshot = builder()
                    .totalSpent("0")
                    .totalWon("0")
                    .netPosition("0")
                    .ticketCount(10)
                    .rollingReturnRate("0.00")
                    .build();

            assertThat(generator.evaluate(snapshot)).isEmpty();
        }
    }

    @Nested
    class LossChasing {

        private final LossChasingInsight generator = new LossChasingInsight(CLOCK);

        @Test
        void fires_whenThreeBorrowsWithinWindow() {
            List<Transaction> recent = new ArrayList<>();
            recent.add(borrowAt(FIXED_INSTANT.minusSeconds(1)));
            recent.add(borrowAt(FIXED_INSTANT.minusSeconds(2)));
            recent.add(borrowAt(FIXED_INSTANT.minusSeconds(3)));
            recent.add(spendAt(FIXED_INSTANT.minusSeconds(4)));
            recent.add(spendAt(FIXED_INSTANT.minusSeconds(5)));

            Optional<Insight> result = generator.evaluate(builder().recentTransactions(recent).build());

            assertThat(result).isPresent();
            Insight insight = result.get();
            assertThat(insight.type()).isEqualTo("LOSS_CHASING");
            assertThat(insight.severity()).isEqualTo(InsightSeverity.CRITICAL);
            assertThat(insight.data()).containsEntry("borrowCount", 3L);
        }

        @Test
        void silent_whenOnlyTwoBorrows() {
            List<Transaction> recent = new ArrayList<>();
            recent.add(borrowAt(FIXED_INSTANT.minusSeconds(1)));
            recent.add(borrowAt(FIXED_INSTANT.minusSeconds(2)));
            recent.add(spendAt(FIXED_INSTANT.minusSeconds(3)));

            assertThat(generator.evaluate(builder().recentTransactions(recent).build())).isEmpty();
        }

        @Test
        void silent_whenBorrowsFallOutsideTheMostRecentWindow() {
            List<Transaction> recent = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                recent.add(spendAt(FIXED_INSTANT.minusSeconds(i + 1)));
            }
            recent.add(borrowAt(FIXED_INSTANT.minusSeconds(1001)));
            recent.add(borrowAt(FIXED_INSTANT.minusSeconds(1002)));
            recent.add(borrowAt(FIXED_INSTANT.minusSeconds(1003)));

            assertThat(generator.evaluate(builder().recentTransactions(recent).build())).isEmpty();
        }
    }

    @Nested
    class LuckyStoreDebunk {

        private final LuckyStoreDebunkInsight generator = new LuckyStoreDebunkInsight(CLOCK);

        @Test
        void fires_whenSpreadExceedsThreshold_andEnoughTickets() {
            Map<UUID, DealerStats> dealers = dealers(dealer("Joe's", "0.72"), dealer("Downtown", "0.55"));

            Optional<Insight> result = generator.evaluate(
                    builder().ticketCount(20).perDealerStats(dealers).build());

            assertThat(result).isPresent();
            Insight insight = result.get();
            assertThat(insight.type()).isEqualTo("LUCKY_STORE_DEBUNK");
            assertThat(insight.severity()).isEqualTo(InsightSeverity.INFO);
            assertThat(insight.data()).containsKey("returnRateSpread");
        }

        @Test
        void silent_whenSpreadExactlyAtThreshold() {
            Map<UUID, DealerStats> dealers = dealers(dealer("Joe's", "0.70"), dealer("Downtown", "0.55"));

            assertThat(generator.evaluate(builder().ticketCount(20).perDealerStats(dealers).build()))
                    .isEmpty();
        }

        @Test
        void silent_whenBelowTicketMinimum() {
            Map<UUID, DealerStats> dealers = dealers(dealer("Joe's", "0.72"), dealer("Downtown", "0.55"));

            assertThat(generator.evaluate(builder().ticketCount(19).perDealerStats(dealers).build()))
                    .isEmpty();
        }

        @Test
        void silent_whenOnlyOneDealer() {
            Map<UUID, DealerStats> dealers = dealers(dealer("Joe's", "0.40"));

            assertThat(generator.evaluate(builder().ticketCount(20).perDealerStats(dealers).build()))
                    .isEmpty();
        }
    }

    @Nested
    class VarianceExplanation {

        private final VarianceExplanationInsight generator = new VarianceExplanationInsight(CLOCK);

        @Test
        void fires_whenBoughtFromThreeBooks() {
            Map<UUID, BookStats> books = books(book("0.82"), book("0.60"), book("0.41"));

            Optional<Insight> result = generator.evaluate(builder().perBookStats(books).build());

            assertThat(result).isPresent();
            Insight insight = result.get();
            assertThat(insight.type()).isEqualTo("VARIANCE_EXPLANATION");
            assertThat(insight.severity()).isEqualTo(InsightSeverity.INFO);
            assertThat(insight.data()).containsEntry("bookCount", 3);
        }

        @Test
        void silent_whenOnlyTwoBooks() {
            Map<UUID, BookStats> books = books(book("0.82"), book("0.41"));

            assertThat(generator.evaluate(builder().perBookStats(books).build())).isEmpty();
        }

        @Test
        void silent_whenPlaceholderBooksDoNotCountTowardThreshold() {
            Map<UUID, BookStats> books = books(book("0.82"), book("0.41"), placeholderBook());

            assertThat(generator.evaluate(builder().perBookStats(books).build())).isEmpty();
        }
    }

    @Nested
    class NearMiss {

        private final NearMissInsight generator = new NearMissInsight(CLOCK);

        @Test
        void fires_whenNearMissCountReachesThreshold() {
            LedgerSnapshot snapshot = builder().revealedLoserCount(10).nearMissCount(5).build();

            Optional<Insight> result = generator.evaluate(snapshot);

            assertThat(result).isPresent();
            Insight insight = result.get();
            assertThat(insight.type()).isEqualTo("NEAR_MISS");
            assertThat(insight.severity()).isEqualTo(InsightSeverity.WARNING);
            assertThat(insight.message()).contains("50%");
        }

        @Test
        void silent_whenBelowNearMissThreshold() {
            LedgerSnapshot snapshot = builder().revealedLoserCount(10).nearMissCount(4).build();

            assertThat(generator.evaluate(snapshot)).isEmpty();
        }
    }

    @Nested
    class InevitabilityCurve {

        private final InevitabilityCurveInsight generator = new InevitabilityCurveInsight(CLOCK);

        @Test
        void fires_whenEnoughTickets_andNetLoss() {
            LedgerSnapshot snapshot = builder()
                    .totalSpent("2500")
                    .totalWon("2000")
                    .netPosition("-500")
                    .ticketCount(25)
                    .rollingReturnRate("0.80")
                    .build();

            Optional<Insight> result = generator.evaluate(snapshot);

            assertThat(result).isPresent();
            Insight insight = result.get();
            assertThat(insight.type()).isEqualTo("INEVITABILITY_CURVE");
            assertThat(insight.severity()).isEqualTo(InsightSeverity.WARNING);
        }

        @Test
        void silent_whenBelowTicketThreshold() {
            LedgerSnapshot snapshot = builder()
                    .totalSpent("2500")
                    .totalWon("2000")
                    .netPosition("-500")
                    .ticketCount(24)
                    .rollingReturnRate("0.80")
                    .build();

            assertThat(generator.evaluate(snapshot)).isEmpty();
        }

        @Test
        void silent_whenNotAtNetLoss() {
            LedgerSnapshot snapshot = builder()
                    .totalSpent("1000")
                    .totalWon("1000")
                    .netPosition("0")
                    .ticketCount(30)
                    .rollingReturnRate("1.00")
                    .build();

            assertThat(generator.evaluate(snapshot)).isEmpty();
        }
    }

    // --- snapshot construction helpers -------------------------------------------------

    private static SnapshotBuilder builder() {
        return new SnapshotBuilder();
    }

    /** Fluent builder for {@link LedgerSnapshot} whose defaults trigger no generator. */
    private static final class SnapshotBuilder {
        private BigDecimal totalBorrowed = new BigDecimal("0");
        private BigDecimal totalSpent = new BigDecimal("1000");
        private BigDecimal totalWon = new BigDecimal("1000");
        private BigDecimal netPosition = new BigDecimal("0");
        private int ticketCount = 1;
        private BigDecimal rollingReturnRate = new BigDecimal("1.00");
        private Map<UUID, DealerStats> perDealerStats = Map.of();
        private Map<UUID, BookStats> perBookStats = Map.of();
        private List<Transaction> recentTransactions = List.of();
        private List<Transaction> sessionBorrowEvents = List.of();
        private int revealedLoserCount = 0;
        private int nearMissCount = 0;

        SnapshotBuilder totalSpent(String value) {
            this.totalSpent = new BigDecimal(value);
            return this;
        }

        SnapshotBuilder totalWon(String value) {
            this.totalWon = new BigDecimal(value);
            return this;
        }

        SnapshotBuilder netPosition(String value) {
            this.netPosition = new BigDecimal(value);
            return this;
        }

        SnapshotBuilder ticketCount(int value) {
            this.ticketCount = value;
            return this;
        }

        SnapshotBuilder rollingReturnRate(String value) {
            this.rollingReturnRate = new BigDecimal(value);
            return this;
        }

        SnapshotBuilder perDealerStats(Map<UUID, DealerStats> value) {
            this.perDealerStats = value;
            return this;
        }

        SnapshotBuilder perBookStats(Map<UUID, BookStats> value) {
            this.perBookStats = value;
            return this;
        }

        SnapshotBuilder recentTransactions(List<Transaction> value) {
            this.recentTransactions = value;
            return this;
        }

        SnapshotBuilder revealedLoserCount(int value) {
            this.revealedLoserCount = value;
            return this;
        }

        SnapshotBuilder nearMissCount(int value) {
            this.nearMissCount = value;
            return this;
        }

        LedgerSnapshot build() {
            return new LedgerSnapshot(
                    totalBorrowed,
                    totalSpent,
                    totalWon,
                    netPosition,
                    ticketCount,
                    rollingReturnRate,
                    perDealerStats,
                    perBookStats,
                    recentTransactions,
                    sessionBorrowEvents,
                    revealedLoserCount,
                    nearMissCount);
        }
    }

    private static Transaction borrowAt(Instant timestamp) {
        return new Transaction(
                UUID.randomUUID(), PLAYER_ID, TransactionType.BORROW, BigDecimal.TEN,
                null, null, null, timestamp);
    }

    private static Transaction spendAt(Instant timestamp) {
        return new Transaction(
                UUID.randomUUID(), PLAYER_ID, TransactionType.SPEND, BigDecimal.TEN,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), timestamp);
    }

    private static DealerStats dealer(String name, String returnRate) {
        return new DealerStats(
                UUID.randomUUID(), name, 5,
                new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal(returnRate));
    }

    private static Map<UUID, DealerStats> dealers(DealerStats... entries) {
        Map<UUID, DealerStats> map = new LinkedHashMap<>();
        for (DealerStats entry : entries) {
            map.put(entry.dealerId(), entry);
        }
        return map;
    }

    private static BookStats book(String returnRate) {
        return new BookStats(
                UUID.randomUUID(), 5,
                new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal(returnRate));
    }

    private static BookStats placeholderBook() {
        return new BookStats(
                UUID.randomUUID(), 0, new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("0"));
    }

    private static Map<UUID, BookStats> books(BookStats... entries) {
        Map<UUID, BookStats> map = new LinkedHashMap<>();
        for (BookStats entry : entries) {
            map.put(entry.bookId(), entry);
        }
        return map;
    }
}
