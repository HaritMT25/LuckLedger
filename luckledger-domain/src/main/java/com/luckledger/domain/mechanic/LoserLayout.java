package com.luckledger.domain.mechanic;

/**
 * How a single losing ({@code $0}) ticket's grid should be constructed.
 *
 * <p>This is the per-ticket instruction a {@link GridPopulator} honors when it builds a loser. It
 * only affects layout, never outcome: whichever layout is requested, the grid still evaluates to
 * {@code $0}. The choice is the mechanic-agnostic contract behind {@code NearMissMode} — the
 * pipeline decides the <em>rate</em> of near-misses, and each populator decides what "one step short
 * of a win" concretely means for its own scoring rule (an extra matching number, an extra seal
 * point, and so on).
 *
 * <p>A winning ticket ignores this flag entirely; a populator handed a positive prize builds that
 * exact prize no matter which layout is passed.
 *
 * @see com.luckledger.domain.generation.NearMissMode
 */
public enum LoserLayout {

    /**
     * A plain loser, built comfortably clear of the win threshold so it reads as an obvious miss and
     * carries no accidental near-miss signal.
     */
    CLEAN,

    /**
     * A loser constructed to land exactly one step short of the win threshold — the manufactured
     * "you almost won" grid. It still pays nothing.
     */
    NEAR_MISS
}
