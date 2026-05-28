package com.luckledger.domain.mechanic;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable outcome of evaluating a populated {@link Grid}.
 *
 * <p>Produced by a {@code WinEvaluator} in two contexts: at generation time (verifying that a
 * reverse-engineered layout yields the intended prize) and at play time (revealing a ticket). The
 * {@code matchDetails} map records, per symbol, how many matches the grid contained — this lets the
 * {@code NearMissAnalyzer} judge how close a losing ticket came to winning without a second
 * evaluation pass (DRY).
 *
 * @param isWinner         whether the grid contains a winning pattern
 * @param prizeAmount      coins awarded by this result; never {@code null}, {@code >= 0}
 *                         (a loser's amount is {@code 0}, or the pool's {@code minPayout})
 * @param winningPositions grid positions forming the winning pattern; never {@code null}, empty for
 *                         a loser. Stored as an unmodifiable, defensively copied list.
 * @param matchDetails     symbol → count of matches found in the grid; never {@code null}. Stored as
 *                         an unmodifiable, defensively copied map.
 */
public record EvaluationResult(
        boolean isWinner,
        BigDecimal prizeAmount,
        List<Position> winningPositions,
        Map<String, Integer> matchDetails) {

    /**
     * Validates invariants and defensively copies the collections on construction.
     *
     * @throws NullPointerException     if {@code prizeAmount}, {@code winningPositions},
     *                                  {@code matchDetails}, or any of their elements is {@code null}
     * @throws IllegalArgumentException if {@code prizeAmount} is negative
     */
    public EvaluationResult {
        Objects.requireNonNull(prizeAmount, "prizeAmount must not be null");
        Objects.requireNonNull(winningPositions, "winningPositions must not be null");
        Objects.requireNonNull(matchDetails, "matchDetails must not be null");
        if (prizeAmount.signum() < 0) {
            throw new IllegalArgumentException("prizeAmount must be >= 0, was: " + prizeAmount);
        }
        winningPositions = List.copyOf(winningPositions);
        matchDetails = Map.copyOf(matchDetails);
    }
}
