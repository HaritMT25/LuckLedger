package com.luckledger.domain.pool;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value object describing the losing tier of a pool.
 *
 * <p>Counterpart to {@link PrizeTier}: where a {@code PrizeTier} models a winning tier with a
 * strictly positive value, a {@code LosingTier} models the tickets that do not win a prize tier.
 * Its {@code value} is the minimum payout floor — {@code 0} when the pool has no floor, or the
 * {@code minPayout} amount when a floor guarantees every ticket pays at least that much.
 *
 * <p>The {@code count} is derived from the pool: {@code totalTickets - sum(winningTier.count)}.
 *
 * @param value              the floor payout per losing ticket; must be non-null and non-negative
 *                           ({@code 0} = no floor, {@code > 0} = guaranteed minimum payout)
 * @param count              the number of losing tickets; must be non-negative
 * @param consolationMessage a human-readable message shown to losers; must be non-null and non-blank
 */
public record LosingTier(BigDecimal value, int count, String consolationMessage) {

    /**
     * Validates the tier invariants on construction.
     *
     * @throws NullPointerException     if {@code value} or {@code consolationMessage} is null
     * @throws IllegalArgumentException if {@code value} is negative, {@code count} is negative,
     *                                  or {@code consolationMessage} is blank
     */
    public LosingTier {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(consolationMessage, "consolationMessage must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("value must not be negative, was: " + value);
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative, was: " + count);
        }
        if (consolationMessage.isBlank()) {
            throw new IllegalArgumentException("consolationMessage must not be blank");
        }
    }

    /**
     * Derives the losing tier for a pool by subtracting the winning tier counts from the total.
     *
     * @param totalTickets       the total number of tickets in the pool
     * @param winningTiers        the winning prize tiers; must be non-null
     * @param value              the floor payout per losing ticket ({@code 0} for no floor)
     * @param consolationMessage the message shown to losers
     * @return a {@code LosingTier} whose count is {@code totalTickets - sum(winningTier.count)}
     * @throws NullPointerException     if {@code winningTiers} is null
     * @throws IllegalArgumentException if the winning tier counts exceed {@code totalTickets}
     */
    public static LosingTier derive(
            int totalTickets, List<PrizeTier> winningTiers, BigDecimal value, String consolationMessage) {
        Objects.requireNonNull(winningTiers, "winningTiers must not be null");
        int winnerCount = winningTiers.stream().mapToInt(PrizeTier::count).sum();
        return new LosingTier(value, totalTickets - winnerCount, consolationMessage);
    }

    /**
     * Computes the floor cost of this tier across all losing tickets.
     *
     * @return {@code value × count}
     */
    public BigDecimal getTierCost() {
        return value.multiply(BigDecimal.valueOf(count));
    }
}
