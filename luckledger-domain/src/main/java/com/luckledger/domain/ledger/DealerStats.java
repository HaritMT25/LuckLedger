package com.luckledger.domain.ledger;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable per-dealer drill-down used in a player's ledger snapshot.
 *
 * <p>Captures how much a player has spent and won at a single dealer, alongside the
 * {@code returnRate} ({@code totalWon / totalSpent}). The return rate is computed upstream
 * and supplied here verbatim; it is never recomputed and may legitimately exceed {@code 1.0}
 * for a player who happened to be lucky at a particular dealer.
 *
 * @param dealerId      stable public identifier of the dealer; never {@code null}
 * @param dealerName    human-readable dealer name; never {@code null} or blank
 * @param ticketsBought number of tickets the player bought from this dealer; {@code >= 0}
 * @param totalSpent    total coins spent at this dealer; never {@code null}, {@code >= 0}
 * @param totalWon      total coins won at this dealer; never {@code null}, {@code >= 0}
 * @param returnRate    {@code totalWon / totalSpent}, supplied upstream; never {@code null}, {@code >= 0}
 */
public record DealerStats(
        UUID dealerId,
        String dealerName,
        int ticketsBought,
        BigDecimal totalSpent,
        BigDecimal totalWon,
        BigDecimal returnRate) {

    /**
     * Validates the per-dealer statistics on construction.
     *
     * @throws NullPointerException     if {@code dealerId}, {@code dealerName}, {@code totalSpent},
     *                                  {@code totalWon}, or {@code returnRate} is {@code null}
     * @throws IllegalArgumentException if {@code dealerName} is blank, {@code ticketsBought} is
     *                                  negative, or any monetary value is negative
     */
    public DealerStats {
        Objects.requireNonNull(dealerId, "dealerId must not be null");
        Objects.requireNonNull(dealerName, "dealerName must not be null");
        if (dealerName.isBlank()) {
            throw new IllegalArgumentException("dealerName must not be blank");
        }
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
