package com.luckledger.domain.ledger;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One point on the "inevitability curve" — cumulative spend vs. win over a player's tickets.
 *
 * <p>The curve plots cumulative spending against cumulative winnings to surface that, over time,
 * the gap widens. Accordingly {@code netPosition} ({@code cumulativeWon - cumulativeSpent}) is
 * normally negative and is allowed to be; it is supplied upstream and never recomputed here.
 *
 * @param ticketNumber    1-based ordinal of the ticket this point represents; {@code >= 1}
 * @param cumulativeSpent running total of coins spent through this ticket; never {@code null}, {@code >= 0}
 * @param cumulativeWon   running total of coins won through this ticket; never {@code null}, {@code >= 0}
 * @param netPosition     {@code cumulativeWon - cumulativeSpent}; never {@code null}, may be negative
 */
public record CurvePoint(
        int ticketNumber,
        BigDecimal cumulativeSpent,
        BigDecimal cumulativeWon,
        BigDecimal netPosition) {

    /**
     * Validates the curve point on construction.
     *
     * @throws NullPointerException     if {@code cumulativeSpent}, {@code cumulativeWon},
     *                                  or {@code netPosition} is {@code null}
     * @throws IllegalArgumentException if {@code ticketNumber} is less than {@code 1}, or if
     *                                  {@code cumulativeSpent} or {@code cumulativeWon} is negative
     */
    public CurvePoint {
        if (ticketNumber < 1) {
            throw new IllegalArgumentException("ticketNumber must be >= 1 but was " + ticketNumber);
        }
        Objects.requireNonNull(cumulativeSpent, "cumulativeSpent must not be null");
        Objects.requireNonNull(cumulativeWon, "cumulativeWon must not be null");
        Objects.requireNonNull(netPosition, "netPosition must not be null");
        if (cumulativeSpent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("cumulativeSpent must be >= 0 but was " + cumulativeSpent);
        }
        if (cumulativeWon.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("cumulativeWon must be >= 0 but was " + cumulativeWon);
        }
    }
}
