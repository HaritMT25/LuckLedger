package com.luckledger.domain.generation;

/**
 * Whether a generated pool's losing tickets are left plain or have near-misses deliberately
 * engineered into them.
 *
 * <p><strong>Why this exists (education-first).</strong> LuckLedger is a gambling-awareness
 * simulator, not a game. A "near-miss" — a losing ticket built to look one symbol, number, or point
 * short of a prize — is one of the most studied dark patterns in real scratch cards: the
 * "I <em>almost</em> won" feeling reliably drives continued play even though it pays nothing. Real
 * products manufacture these on purpose at a far higher rate than chance alone would produce, and
 * LuckLedger's UI tells players so. This mode is the switch that makes that claim <em>true</em> in
 * the generated pools instead of merely asserted in the copy.
 *
 * <p><strong>RTP is untouched either way.</strong> Near-miss engineering only changes how a
 * {@code $0} loser's grid is <em>arranged</em>; it never changes which tickets are losers, how many
 * there are, or any prize amount. Both modes ship the exact same tier counts and the exact same
 * summed payout, so the return-to-player is identical — the "payout ratio is sacred" invariant holds
 * regardless of the mode chosen.
 *
 * @see com.luckledger.domain.mechanic.LoserLayout
 */
public enum NearMissMode {

    /**
     * Losers carry no engineered near-misses. Any "close" loser that appears is purely incidental,
     * and the populators deliberately steer clean losers <em>away</em> from the near-miss boundary,
     * so a clean pool is a useful control: it shows how rarely a fair game would tease a win.
     */
    CLEAN,

    /**
     * A DESIGN-mandated share of losers (see {@code GenerationPipeline.ENGINEERED_NEAR_MISS_RATE})
     * are constructed as near-misses — each built to sit exactly one step short of the win threshold.
     * This models how a commercial scratch card is really tuned and lets the educational layer
     * surface the manufactured "almost won" rate honestly.
     */
    REALISTIC
}
