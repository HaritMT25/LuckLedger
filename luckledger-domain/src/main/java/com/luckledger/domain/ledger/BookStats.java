package com.luckledger.domain.ledger;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable per-book drill-down used in a player's ledger snapshot.
 *
 * <p>Captures how much a player has spent and won from a single ticket book, alongside the
 * {@code returnRate} ({@code totalWon / totalSpent}). Books have no human name, so unlike
 * {@link DealerStats} this type carries only the {@code bookId}. The return rate is computed
 * upstream and supplied verbatim; it is never recomputed and may legitimately exceed {@code 1.0}.
 *
 * @param bookId        stable public identifier of the ticket book; never {@code null}
 * @param ticketsBought number of tickets the player bought from this book; {@code >= 0}
 * @param totalSpent    total coins spent on this book; never {@code null}, {@code >= 0}
 * @param totalWon      total coins won from this book; never {@code null}, {@code >= 0}
 * @param returnRate    {@code totalWon / totalSpent}, supplied upstream; never {@code null}, {@code >= 0}
 */
public record BookStats(
        UUID bookId,
        int ticketsBought,
        BigDecimal totalSpent,
        BigDecimal totalWon,
        BigDecimal returnRate) {

    /**
     * Validates the per-book statistics on construction.
     *
     * @throws NullPointerException     if {@code bookId}, {@code totalSpent}, {@code totalWon},
     *                                  or {@code returnRate} is {@code null}
     * @throws IllegalArgumentException if {@code ticketsBought} is negative or any monetary
     *                                  value is negative
     */
    public BookStats {
        Objects.requireNonNull(bookId, "bookId must not be null");
        if (ticketsBought < 0) {
            throw new IllegalArgumentException("ticketsBought must be >= 0 but was " + ticketsBought);
        }
        Objects.requireNonNull(totalSpent, "totalSpent must not be null");
        Objects.requireNonNull(totalWon, "totalWon must not be null");
        Objects.requireNonNull(returnRate, "returnRate must not be null");
        if (totalSpent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("totalSpent must be >= 0 but was " + totalSpent);
        }
        if (totalWon.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("totalWon must be >= 0 but was " + totalWon);
        }
        if (returnRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("returnRate must be >= 0 but was " + returnRate);
        }
    }
}
