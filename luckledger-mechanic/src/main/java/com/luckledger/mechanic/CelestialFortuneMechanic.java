package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.GridPopulator;
import com.luckledger.domain.mechanic.MechanicType;
import java.util.List;
import java.util.stream.IntStream;

/**
 * {@link GameMechanic} for Celestial Fortune, the hypergeometric number-match game.
 *
 * <p>This factory bundles the two halves of the mechanic — a {@link CelestialFortunePopulator}
 * (reverse-engineers a grid from a predetermined prize) and a {@link CelestialFortuneEvaluator}
 * (reads the prize back from a grid) — so a layout the populator builds for a given prize evaluates
 * to exactly that prize. The generation pipeline depends on this {@link GameMechanic}, never on the
 * concrete populator or evaluator (Dependency Inversion); a new mechanic plugs in by implementing
 * {@link GameMechanic} with no change to existing code (Open/Closed).
 *
 * <p><strong>Probability model.</strong> A pool of {@value CelestialFortuneEvaluator#NUMBER_POOL_SIZE}
 * numbers (N), {@value CelestialFortuneEvaluator#WINNING_NUMBER_COUNT} winning (n), and
 * {@value CelestialFortuneEvaluator#PLAYER_NUMBER_COUNT} player picks (m); the chance of exactly
 * {@code k} matches is hypergeometric, {@code P(k) = C(8,k) * C(22,4-k) / C(30,4)}, calibrated to a
 * 65.2% return-to-player on a $5 ticket. See {@link CelestialFortuneEvaluator} for the prize ladder.
 *
 * <p>This class is stateless and thread-safe; the per-call instances it hands out carry whatever
 * mutable state (a randomness source) the populator needs.
 */
public final class CelestialFortuneMechanic implements GameMechanic {

    /**
     * The default number pool: the strings {@code "1"} through
     * {@code "30"} ({@value CelestialFortuneEvaluator#NUMBER_POOL_SIZE} distinct numbers, matching the
     * hypergeometric pool size N). Immutable.
     */
    private static final List<String> DEFAULT_SYMBOL_POOL =
            IntStream.rangeClosed(1, CelestialFortuneEvaluator.NUMBER_POOL_SIZE)
                    .mapToObj(Integer::toString)
                    .toList();

    @Override
    public MechanicType getType() {
        return MechanicType.CELESTIAL_FORTUNE;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a fresh {@link CelestialFortunePopulator} on each call, each backed by its own
     * {@link java.security.SecureRandom}, so concurrent callers never share randomness state.
     */
    @Override
    public GridPopulator createPopulator() {
        return new CelestialFortunePopulator();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a {@link CelestialFortuneEvaluator}, the counterpart of {@link #createPopulator()};
     * the evaluator is stateless, so a fresh instance is interchangeable with any other.
     */
    @Override
    public WinEvaluator createEvaluator() {
        return new CelestialFortuneEvaluator();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The numbers {@code "1"}–{@code "30"}: exactly the
     * {@value CelestialFortuneEvaluator#NUMBER_POOL_SIZE} distinct numbers the populator draws the
     * winning and player picks from.
     */
    @Override
    public List<String> getDefaultSymbolPool() {
        return DEFAULT_SYMBOL_POOL;
    }
}
