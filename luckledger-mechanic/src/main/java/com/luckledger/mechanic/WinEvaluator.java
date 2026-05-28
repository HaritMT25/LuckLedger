package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;

/**
 * Evaluates a populated {@link Grid} and reports whether it forms a winning pattern.
 *
 * <p>A single evaluator instance is reused across two contexts (DRY): at generation time, to verify
 * that a constructively built layout yields the intended prize, and at play time, to reveal a
 * ticket. Because the same instance serves both, implementations MUST be stateless and side-effect
 * free — {@link #evaluate(Grid)} must depend only on its argument so repeated calls on different
 * grids are independent.
 */
@FunctionalInterface
public interface WinEvaluator {

    /**
     * Evaluates the given grid for a winning pattern.
     *
     * @param grid the populated grid to evaluate; never {@code null}
     * @return the outcome, including prize amount, winning positions, and per-symbol match counts;
     *     never {@code null}
     */
    EvaluationResult evaluate(Grid grid);
}
