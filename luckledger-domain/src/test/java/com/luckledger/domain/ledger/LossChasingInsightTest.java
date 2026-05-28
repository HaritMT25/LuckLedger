package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LossChasingInsightTest {

    private static final Instant FIXED = Instant.parse("2026-05-28T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    private final LossChasingInsight generator = new LossChasingInsight(FIXED_CLOCK);

    @Test
    void shouldDetectLossChasing_whenThreeBorrowsInLastTenTransactions() {
        List<Transaction> recent =
                List.of(borrow(0), spend(1), borrow(2), spend(3), borrow(4), win(5));

        Optional<Insight> result = generator.evaluate(snapshotWith(recent));

        assertThat(result).isPresent();
        Insight insight = result.get();
        assertThat(insight.type()).isEqualTo("LOSS_CHASING");
        assertThat(insight.severity()).isEqualTo(InsightSeverity.CRITICAL);
        assertThat(insight.title()).isNotBlank();
        assertThat(insight.message()).contains("3").contains("loss chasing");
        assertThat(insight.timestamp()).isEqualTo(FIXED);
    }

    @Test
    void shouldNotDetect_whenFewerThanThreeBorrows() {
        List<Transaction> recent = List.of(borrow(0), spend(1), borrow(2), spend(3), win(4));

        assertThat(generator.evaluate(snapshotWith(recent))).isEmpty();
    }

    @Test
    void shouldNotDetect_whenNoTransactions() {
        assertThat(generator.evaluate(snapshotWith(List.of()))).isEmpty();
    }

    @Test
    void shouldDetect_whenAllTransactionsAreBorrows() {
        List<Transaction> recent = List.of(borrow(0), borrow(1), borrow(2));

        assertThat(generator.evaluate(snapshotWith(recent))).isPresent();
    }

    @Test
    void shouldExcludeBorrowsOlderThanTheLastTenTransactions() {
        List<Transaction> recent = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            recent.add(borrow(i)); // four old borrows
        }
        for (int i = 4; i < 14; i++) {
            recent.add(spend(i)); // ten newer spends fill the window
        }

        assertThat(generator.evaluate(snapshotWith(recent))).isEmpty();
    }

    @Test
    void shouldDetect_whenThreeBorrowsAreAmongTheMostRecentTen() {
        List<Transaction> recent = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            recent.add(spend(i)); // ten old spends
        }
        recent.add(borrow(10));
        recent.add(borrow(11));
        recent.add(borrow(12)); // three newest borrows

        assertThat(generator.evaluate(snapshotWith(recent))).isPresent();
    }

    @Test
    void shouldBeOrderIndependent_usingTimestampsToFindRecentWindow() {
        List<Transaction> recent = new ArrayList<>();
        recent.add(borrow(10));
        recent.add(borrow(11));
        recent.add(borrow(12)); // three newest borrows
        for (int i = 0; i < 10; i++) {
            recent.add(spend(i));
        }
        Collections.shuffle(recent, new Random(42));

        assertThat(generator.evaluate(snapshotWith(recent))).isPresent();
    }

    @Test
    void shouldReportActualBorrowCountInMessageAndData() {
        List<Transaction> recent =
                List.of(borrow(0), borrow(1), borrow(2), borrow(3), spend(4));

        Insight insight = generator.evaluate(snapshotWith(recent)).orElseThrow();

        assertThat(insight.message()).contains("4");
        assertThat(insight.data()).containsEntry("borrowCount", 4L);
    }

    @Test
    void shouldRejectNullSnapshot() {
        assertThatNullPointerException().isThrownBy(() -> generator.evaluate(null));
    }

    @Test
    void shouldUseSystemClock_withDefaultConstructor() {
        LossChasingInsight defaultGenerator = new LossChasingInsight();
        List<Transaction> recent = List.of(borrow(0), borrow(1), borrow(2));

        Insight insight = defaultGenerator.evaluate(snapshotWith(recent)).orElseThrow();

        assertThat(insight.timestamp()).isNotNull();
    }

    private Transaction borrow(int secondsOffset) {
        return tx(TransactionType.BORROW, secondsOffset);
    }

    private Transaction spend(int secondsOffset) {
        return tx(TransactionType.SPEND, secondsOffset);
    }

    private Transaction win(int secondsOffset) {
        return tx(TransactionType.WIN, secondsOffset);
    }

    private Transaction tx(TransactionType type, int secondsOffset) {
        return new Transaction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                type,
                BigDecimal.ONE,
                null,
                null,
                null,
                Instant.parse("2026-05-28T00:00:00Z").plusSeconds(secondsOffset));
    }

    private LedgerSnapshot snapshotWith(List<Transaction> recentTransactions) {
        return new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                recentTransactions,
                List.of());
    }
}
