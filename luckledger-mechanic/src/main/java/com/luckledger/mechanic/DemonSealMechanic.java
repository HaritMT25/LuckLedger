package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.GridPopulator;
import com.luckledger.domain.mechanic.MechanicType;
import java.util.List;

/**
 * {@link GameMechanic} for Demon Seal, the trinomial seal-reveal game.
 *
 * <p>This factory bundles the two halves of the mechanic — a {@link DemonSealPopulator}
 * (reverse-engineers a six-seal layout from a predetermined prize) and a {@link DemonSealEvaluator}
 * (reads the prize back from the seals on a grid) — so a layout the populator builds for a given
 * prize evaluates to exactly that prize. The generation pipeline depends on this {@link GameMechanic},
 * never on the concrete populator or evaluator (Dependency Inversion); a new mechanic plugs in by
 * implementing {@link GameMechanic} with no change to existing code (Open/Closed).
 *
 * <p><strong>Probability model.</strong> Six seals, each independently gold, silver, or broken with
 * {@code P(gold)=0.12, P(silver)=0.40, P(broken)=0.48}; the score {@code T = 2·gold + silver} ranges
 * over {@code 0..12} and follows the trinomial of six draws. The prize ladder is calibrated to a
 * 64.4% return-to-player on a $5 ticket (top prize {@code T=12} at roughly 1 in 335,000). See
 * {@link DemonSealEvaluator} for the prize ladder.
 *
 * <p>This class is stateless and thread-safe; the per-call instances it hands out carry whatever
 * mutable state (a randomness source) the populator needs.
 */
public final class DemonSealMechanic implements GameMechanic {

    /**
     * Default symbols for a Demon Seal ticket: the three scoring seals plus themed decoy filler. The
     * populator builds the six scoring seals from its own constants and draws only <em>non-seal</em>
     * symbols from this pool to fill the remaining cells, so the pool must offer at least one non-seal
     * symbol — the three decoys ({@code "RUNE"}, {@code "SKULL"}, {@code "CHAIN"}) satisfy that.
     * Immutable.
     */
    private static final List<String> DEFAULT_SYMBOL_POOL =
            List.of(
                    DemonSealEvaluator.GOLD_SEAL,
                    DemonSealEvaluator.SILVER_SEAL,
                    DemonSealEvaluator.BROKEN_SEAL,
                    "RUNE",
                    "SKULL",
                    "CHAIN");

    @Override
    public MechanicType getType() {
        return MechanicType.DEMON_SEAL;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a fresh {@link DemonSealPopulator} on each call, each backed by its own
     * {@link java.security.SecureRandom}, so concurrent callers never share randomness state.
     */
    @Override
    public GridPopulator createPopulator() {
        return new DemonSealPopulator();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a {@link DemonSealEvaluator}, the counterpart of {@link #createPopulator()}; the
     * evaluator is stateless, so a fresh instance is interchangeable with any other.
     */
    @Override
    public WinEvaluator createEvaluator() {
        return new DemonSealEvaluator();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The three seal symbols plus themed decoy filler. The seals are intrinsic to the mechanic
     * (the populator places them from its own constants); the decoys give the populator non-seal
     * symbols for the remaining cells.
     */
    @Override
    public List<String> getDefaultSymbolPool() {
        return DEFAULT_SYMBOL_POOL;
    }
}
