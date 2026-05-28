package com.luckledger.domain.pool;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable value object describing a single winning prize tier within a pool.
 *
 * <p>Represents WINNING tiers only — {@code value} is strictly positive. Losing tiers
 * are modelled separately and derived from the pool's total ticket count.
 *
 * @param value the prize amount paid per ticket in this tier; must be non-null and strictly positive
 * @param count the number of tickets in this tier; must be strictly positive
 * @param label a human-readable name for the tier; must be non-null and non-blank
 */
public record PrizeTier(BigDecimal value, int count, String label) {

    /**
     * Validates the tier invariants on construction.
     *
     * @throws NullPointerException     if {@code value} or {@code label} is null
     * @throws IllegalArgumentException if {@code value} is not strictly positive,
     *                                  {@code count} is not strictly positive,
     *                                  or {@code label} is blank
     */
    public PrizeTier {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(label, "label must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("value must be strictly positive, was: " + value);
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be strictly positive, was: " + count);
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }

    /**
     * Computes the total cost of this tier across all its tickets.
     *
     * @return {@code value × count}
     */
    public BigDecimal getTierCost() {
        return value.multiply(BigDecimal.valueOf(count));
    }
}
