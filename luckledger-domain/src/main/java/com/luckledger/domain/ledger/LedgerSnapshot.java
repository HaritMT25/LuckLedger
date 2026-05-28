package com.luckledger.domain.ledger;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable read-only aggregate of a player's ledger state at a single point in time.
 *
 * <p>Consumed by {@code InsightGenerator} implementations, which examine a snapshot and decide
 * whether an educational observation applies. The hot-path totals ({@code totalBorrowed},
 * {@code totalSpent}, {@code totalWon}, {@code netPosition}, {@code ticketCount},
 * {@code rollingReturnRate}) are computed upstream and supplied verbatim — they are never
 * recomputed here, and {@code netPosition} and {@code rollingReturnRate} are not cross-validated
 * against the other totals.
 *
 * <p>{@code netPosition} ({@code totalWon - totalSpent}) is routinely negative — that is the
 * point of the simulator. {@code rollingReturnRate} ({@code totalWon / totalSpent}) may exceed
 * {@code 1.0} for a player who is temporarily ahead. The four collections are defensively copied
 * into unmodifiable views on construction so the snapshot is a true value object.
 *
 * @param totalBorrowed       total coins borrowed from the bank; never {@code null}, {@code >= 0}
 * @param totalSpent          total coins spent on tickets; never {@code null}, {@code >= 0}
 * @param totalWon            total coins won from tickets; never {@code null}, {@code >= 0}
 * @param netPosition         {@code totalWon - totalSpent}, supplied upstream; never {@code null}, may be negative
 * @param ticketCount         number of tickets purchased; {@code >= 0}
 * @param rollingReturnRate   {@code totalWon / totalSpent}, supplied upstream; never {@code null}, {@code >= 0}
 * @param perDealerStats      per-dealer drill-down keyed by dealer id; never {@code null}, copied unmodifiable
 * @param perBookStats        per-book drill-down keyed by book id; never {@code null}, copied unmodifiable
 * @param recentTransactions  last N transactions for streak/pattern detection; never {@code null}, copied unmodifiable
 * @param sessionBorrowEvents borrow events in the current session for loss-chasing detection; never {@code null}, copied unmodifiable
 * @param revealedLoserCount  number of revealed losing tickets; {@code >= 0}. Denominator for the
 *                            near-miss rate the near-miss educational observation reports
 * @param nearMissCount       number of revealed losing tickets whose {@code matchDetails} showed a
 *                            near-miss (e.g. 2-of-3 matching symbols); {@code >= 0} and
 *                            {@code <= revealedLoserCount}, since a near-miss is itself a loser
 */
public record LedgerSnapshot(
        BigDecimal totalBorrowed,
        BigDecimal totalSpent,
        BigDecimal totalWon,
        BigDecimal netPosition,
        int ticketCount,
        BigDecimal rollingReturnRate,
        Map<UUID, DealerStats> perDealerStats,
        Map<UUID, BookStats> perBookStats,
        List<Transaction> recentTransactions,
        List<Transaction> sessionBorrowEvents,
        int revealedLoserCount,
        int nearMissCount) {

    /**
     * Validates the snapshot and defensively copies its collections on construction.
     *
     * @throws NullPointerException     if any monetary value, {@code rollingReturnRate}, or any
     *                                  collection (or one of its keys, values, or elements) is {@code null}
     * @throws IllegalArgumentException if {@code totalBorrowed}, {@code totalSpent}, {@code totalWon},
     *                                  or {@code rollingReturnRate} is negative, {@code ticketCount},
     *                                  {@code revealedLoserCount}, or {@code nearMissCount} is negative,
     *                                  or {@code nearMissCount} exceeds {@code revealedLoserCount}
     */
    public LedgerSnapshot {
        Objects.requireNonNull(totalBorrowed, "totalBorrowed must not be null");
        Objects.requireNonNull(totalSpent, "totalSpent must not be null");
        Objects.requireNonNull(totalWon, "totalWon must not be null");
        Objects.requireNonNull(netPosition, "netPosition must not be null");
        Objects.requireNonNull(rollingReturnRate, "rollingReturnRate must not be null");
        if (totalBorrowed.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("totalBorrowed must be >= 0 but was " + totalBorrowed);
        }
        if (totalSpent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("totalSpent must be >= 0 but was " + totalSpent);
        }
        if (totalWon.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("totalWon must be >= 0 but was " + totalWon);
        }
        if (ticketCount < 0) {
            throw new IllegalArgumentException("ticketCount must be >= 0 but was " + ticketCount);
        }
        if (rollingReturnRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("rollingReturnRate must be >= 0 but was " + rollingReturnRate);
        }
        if (revealedLoserCount < 0) {
            throw new IllegalArgumentException("revealedLoserCount must be >= 0 but was " + revealedLoserCount);
        }
        if (nearMissCount < 0) {
            throw new IllegalArgumentException("nearMissCount must be >= 0 but was " + nearMissCount);
        }
        if (nearMissCount > revealedLoserCount) {
            throw new IllegalArgumentException(
                    "nearMissCount (" + nearMissCount + ") must not exceed revealedLoserCount (" + revealedLoserCount + ")");
        }
        Objects.requireNonNull(perDealerStats, "perDealerStats must not be null");
        Objects.requireNonNull(perBookStats, "perBookStats must not be null");
        Objects.requireNonNull(recentTransactions, "recentTransactions must not be null");
        Objects.requireNonNull(sessionBorrowEvents, "sessionBorrowEvents must not be null");
        perDealerStats = Map.copyOf(perDealerStats);
        perBookStats = Map.copyOf(perBookStats);
        recentTransactions = List.copyOf(recentTransactions);
        sessionBorrowEvents = List.copyOf(sessionBorrowEvents);
    }

    /**
     * Convenience constructor for snapshots that do not track near-miss telemetry, defaulting both
     * {@code revealedLoserCount} and {@code nearMissCount} to {@code 0}.
     *
     * @see #LedgerSnapshot(BigDecimal, BigDecimal, BigDecimal, BigDecimal, int, BigDecimal, Map, Map, List, List, int, int)
     */
    public LedgerSnapshot(
            BigDecimal totalBorrowed,
            BigDecimal totalSpent,
            BigDecimal totalWon,
            BigDecimal netPosition,
            int ticketCount,
            BigDecimal rollingReturnRate,
            Map<UUID, DealerStats> perDealerStats,
            Map<UUID, BookStats> perBookStats,
            List<Transaction> recentTransactions,
            List<Transaction> sessionBorrowEvents) {
        this(
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
                0,
                0);
    }
}
