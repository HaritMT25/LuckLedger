package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.GridPopulator;
import com.luckledger.domain.mechanic.MechanicType;
import java.util.List;

/**
 * Factory bundling the {@link GridPopulator} and {@link WinEvaluator} that together define one
 * scratch-card mechanic (Match-3, Number Match, Celestial Fortune, Demon Seal, ...).
 *
 * <p>This interface is the generation pipeline's Open/Closed extension point: a new mechanic is
 * added by implementing {@code GameMechanic}, with no change to the pool contract, book partition,
 * dealer allocation, ledger, or any existing mechanic. The pipeline depends on this interface, not
 * on concrete populators or evaluators (Dependency Inversion).
 *
 * <p>Populating a grid and evaluating a grid are kept as separate interfaces ({@link GridPopulator},
 * {@link WinEvaluator}) rather than a single combined engine (Interface Segregation); a mechanic
 * pairs the two halves so that a grid the populator builds for a given prize is read back by the
 * evaluator as exactly that prize.
 */
public interface GameMechanic {

    /**
     * Identifies which mechanic this factory produces.
     *
     * @return the mechanic type; never {@code null}
     */
    MechanicType getType();

    /**
     * Supplies a populator that reverse-engineers grids from predetermined outcomes for this
     * mechanic.
     *
     * @return a {@link GridPopulator} for this mechanic; never {@code null}
     */
    GridPopulator createPopulator();

    /**
     * Supplies an evaluator that reads the outcome of a grid for this mechanic. The returned
     * evaluator is the counterpart of {@link #createPopulator()} — a grid the populator builds for a
     * prize must evaluate to exactly that prize.
     *
     * @return a {@link WinEvaluator} for this mechanic; never {@code null}
     */
    WinEvaluator createEvaluator();

    /**
     * The abstract symbols appropriate for this mechanic (numbers for NUMBER_MATCH, icons for
     * MATCH_3, letters for CROSSWORD, ...). Callers pass this pool to the populator unless they
     * supply their own.
     *
     * @return a non-empty list of symbols; never {@code null}
     */
    List<String> getDefaultSymbolPool();
}
