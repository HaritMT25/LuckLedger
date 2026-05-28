package com.luckledger.distribution;

/**
 * A dealer's rank tier, derived from how many books they have depleted over their lifetime. The tier
 * controls the <em>quality</em> slice of the book-value distribution a dealer receives (via
 * {@link AllocationQuartile}), never the <em>quantity</em> — that throughput cap is equal for all
 * dealers, which is what stops a "lucky store" flywheel.
 */
public enum DealerTier {
    TIER_1,
    TIER_2,
    TIER_3
}
