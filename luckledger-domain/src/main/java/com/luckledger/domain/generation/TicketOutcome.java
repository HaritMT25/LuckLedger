package com.luckledger.domain.generation;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A single pre-generated ticket outcome — the Layer 2 product of the generation pipeline, before any
 * grid or visuals exist. It pairs a stable public {@code outcomeId} with the {@code prizeAmount} that
 * ticket will pay, and nothing else: outcomes are determined in full before any ticket is rendered
 * (the "pre-generated pools" invariant), then shuffled into a book.
 *
 * <p>Whether an outcome counts as a win is relative to the pool's guaranteed minimum payout: a winner
 * pays strictly more than {@code minPayout}, while a loser pays either nothing or exactly the floor
 * (a break-even consolation is not a "win"). The no-argument {@link #isWinner()} / {@link #isLoser()}
 * treat the floor as zero, the default for pure-loser pools.
 *
 * <p>Money is {@link BigDecimal} (never {@code double}); {@code prizeAmount} is non-negative.
 */
public record TicketOutcome(UUID outcomeId, BigDecimal prizeAmount) {

    public TicketOutcome {
        Objects.requireNonNull(outcomeId, "outcomeId must not be null");
        Objects.requireNonNull(prizeAmount, "prizeAmount must not be null");
        if (prizeAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "prizeAmount must be >= 0, was " + prizeAmount.toPlainString());
        }
    }

    /**
     * Whether this outcome pays strictly more than the pool's guaranteed minimum payout.
     *
     * @param minPayout the pool's guaranteed minimum payout per ticket; never {@code null}
     * @return {@code true} when {@code prizeAmount > minPayout}
     */
    public boolean isWinner(BigDecimal minPayout) {
        Objects.requireNonNull(minPayout, "minPayout must not be null");
        return prizeAmount.compareTo(minPayout) > 0;
    }

    /**
     * Whether this outcome is a loser: it pays nothing, or exactly the guaranteed minimum payout (a
     * break-even consolation that is not a true win).
     *
     * @param minPayout the pool's guaranteed minimum payout per ticket; never {@code null}
     * @return {@code true} when {@code prizeAmount == 0} or {@code prizeAmount == minPayout}
     */
    public boolean isLoser(BigDecimal minPayout) {
        Objects.requireNonNull(minPayout, "minPayout must not be null");
        return prizeAmount.signum() == 0 || prizeAmount.compareTo(minPayout) == 0;
    }

    /** Convenience for pure-loser pools (floor {@code 0}): a winner pays a positive prize. */
    public boolean isWinner() {
        return isWinner(BigDecimal.ZERO);
    }

    /** Convenience for pure-loser pools (floor {@code 0}): a loser pays nothing. */
    public boolean isLoser() {
        return isLoser(BigDecimal.ZERO);
    }
}
