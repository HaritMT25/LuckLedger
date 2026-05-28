package com.luckledger.distribution;

import java.util.Objects;

/**
 * The band of the value-sorted book distribution a dealer is eligible for. Books are sorted by value
 * ascending and split into three segments; a dealer's {@link DealerTier} maps to the matching band,
 * so higher-tier dealers receive the higher-value books — quality, not quantity.
 */
public enum AllocationQuartile {
    LOWER,
    MIDDLE,
    UPPER;

    /**
     * Maps a dealer tier to its allocation band: {@code TIER_1 -> LOWER}, {@code TIER_2 -> MIDDLE},
     * {@code TIER_3 -> UPPER}.
     */
    public static AllocationQuartile fromTier(DealerTier tier) {
        Objects.requireNonNull(tier, "tier must not be null");
        return switch (tier) {
            case TIER_1 -> LOWER;
            case TIER_2 -> MIDDLE;
            case TIER_3 -> UPPER;
        };
    }
}
