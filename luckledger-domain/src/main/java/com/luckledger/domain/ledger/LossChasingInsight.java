package com.luckledger.domain.ledger;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Flags loss chasing: borrowing more coins to recover from losses.
 *
 * <p>Triggers when at least {@value #BORROW_THRESHOLD} of the player's most recent
 * {@value #WINDOW_SIZE} transactions are {@link TransactionType#BORROW} events. The window is
 * resolved by {@link Transaction#timestamp()} rather than list order, so the snapshot's
 * {@code recentTransactions} may be supplied in any order. Repeated borrowing in a short span
 * is the canonical loss-chasing signal, so the produced insight is {@link InsightSeverity#CRITICAL}.
 */
public final class LossChasingInsight implements InsightGenerator {

    /** Stable machine identifier for this insight kind. */
    static final String TYPE = "LOSS_CHASING";

    /** Number of most-recent transactions examined for borrow events. */
    static final int WINDOW_SIZE = 10;

    /** Minimum borrow events within the window that trigger the insight. */
    static final int BORROW_THRESHOLD = 3;

    private final Clock clock;

    /** Creates a generator that timestamps insights from the system UTC clock. */
    public LossChasingInsight() {
        this(Clock.systemUTC());
    }

    /**
     * Creates a generator that timestamps insights from the supplied clock.
     *
     * @param clock clock used to stamp produced insights; never {@code null}
     */
    public LossChasingInsight(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Produces a loss-chasing insight when the recent-transaction window contains enough borrows.
     *
     * @param snapshot the read-only ledger state to evaluate; never {@code null}
     * @return a {@link InsightSeverity#CRITICAL} insight if at least {@value #BORROW_THRESHOLD}
     *         of the most recent {@value #WINDOW_SIZE} transactions are borrows, otherwise empty
     * @throws NullPointerException if {@code snapshot} is {@code null}
     */
    @Override
    public Optional<Insight> evaluate(LedgerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        long borrowCount = snapshot.recentTransactions().stream()
                .sorted(Comparator.comparing(Transaction::timestamp).reversed())
                .limit(WINDOW_SIZE)
                .filter(transaction -> transaction.type() == TransactionType.BORROW)
                .count();
        if (borrowCount < BORROW_THRESHOLD) {
            return Optional.empty();
        }
        String message = "You've borrowed coins " + borrowCount + " times in the last "
                + WINDOW_SIZE + " transactions. In real gambling, this pattern is called loss chasing.";
        Insight insight = new Insight(
                TYPE,
                InsightSeverity.CRITICAL,
                "Loss chasing detected",
                message,
                Map.of("borrowCount", borrowCount, "windowSize", WINDOW_SIZE),
                Instant.now(clock));
        return Optional.of(insight);
    }
}
