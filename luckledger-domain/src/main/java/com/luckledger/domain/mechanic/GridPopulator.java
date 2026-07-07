package com.luckledger.domain.mechanic;

import java.util.List;

/**
 * Strategy for building a {@link Grid} from a predetermined outcome.
 *
 * <p>Population is the reverse-engineering stage of the generation pipeline: the prize amount is
 * decided <em>first</em>, then a valid symbol layout that produces exactly that outcome is
 * constructed. A non-zero {@code prizeAmount} must yield a grid whose winning pattern (as defined
 * by the mechanic's evaluator) pays exactly that amount; a {@code prizeAmount} of {@code 0} must
 * yield a grid with no accidental winning pattern.
 *
 * <p>Implementations MUST be constructive — a single forward pass that places symbols to satisfy
 * the outcome. Reject-and-retry (populate randomly, then check whether the result matches) is
 * prohibited: it makes generation time unbounded and the math non-deterministic.
 *
 * @see Grid
 * @see GridSize
 */
@FunctionalInterface
public interface GridPopulator {

    /**
     * Builds a grid of the requested size whose layout encodes the given outcome.
     *
     * @param size the grid dimensions to produce
     * @param prizeAmount the predetermined prize for this ticket; {@code 0} for a loser
     * @param symbolPool the available abstract symbols to place in cells; must be non-empty
     * @return a fully populated {@link Grid} of the requested size
     */
    Grid populate(GridSize size, double prizeAmount, List<String> symbolPool);

    /**
     * Builds a grid, additionally choosing how a losing ({@code $0}) ticket should be laid out.
     *
     * <p>This is the near-miss-aware entry point. The {@code layout} is <em>advisory</em> and only
     * meaningful for losers: a populator that supports engineered near-misses honors it for {@code $0}
     * tickets, while a winner is built to its exact prize regardless. The default implementation
     * ignores {@code layout} and delegates to {@link #populate(GridSize, double, List)}, so this
     * interface stays a {@link FunctionalInterface} (a single abstract method) and every existing
     * lambda, method reference, and implementation keeps working unchanged. Populators that engineer
     * near-misses override this method.
     *
     * @param size the grid dimensions to produce
     * @param prizeAmount the predetermined prize for this ticket; {@code 0} for a loser
     * @param symbolPool the available abstract symbols to place in cells; must be non-empty
     * @param layout how to shape a losing grid ({@link LoserLayout#CLEAN} or
     *     {@link LoserLayout#NEAR_MISS}); ignored for winners and by populators that do not engineer
     *     near-misses
     * @return a fully populated {@link Grid} of the requested size, still evaluating to
     *     {@code prizeAmount}
     */
    default Grid populate(
            GridSize size, double prizeAmount, List<String> symbolPool, LoserLayout layout) {
        return populate(size, prizeAmount, symbolPool);
    }
}
