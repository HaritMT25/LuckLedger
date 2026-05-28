package com.luckledger.distribution;

import java.util.List;
import java.util.Objects;

/**
 * Maps a dealer's lifetime {@code booksDepleted} count to a {@link DealerTier}. Ranking is driven by
 * cumulative throughput, so a dealer climbs tiers as it sells more — but, per §3.6, tier only changes
 * <em>which</em> books a dealer is eligible for, never how many.
 *
 * <p>The thresholds are tuning parameters, not invariants.
 */
public final class DealerTierResolver {

    /** A dealer with fewer than this many depleted books is {@link DealerTier#TIER_1}. */
    public static final int TIER_1_CEILING = 10;
    /** A dealer below this (and at/above {@link #TIER_1_CEILING}) is {@link DealerTier#TIER_2}. */
    public static final int TIER_2_CEILING = 50;

    /**
     * Resolves a dealer's tier from its {@code booksDepleted}. Pure — does not mutate the dealer.
     *
     * @param dealer the dealer; never {@code null}
     * @return the resolved tier
     */
    public DealerTier resolve(Dealer dealer) {
        Objects.requireNonNull(dealer, "dealer must not be null");
        int depleted = dealer.booksDepleted();
        if (depleted < TIER_1_CEILING) {
            return DealerTier.TIER_1;
        }
        if (depleted < TIER_2_CEILING) {
            return DealerTier.TIER_2;
        }
        return DealerTier.TIER_3;
    }

    /**
     * Refreshes every dealer's tier in place; called once at the start of an allocation cycle.
     *
     * @param dealers the dealers to update; never {@code null}
     */
    public void resolveAll(List<Dealer> dealers) {
        Objects.requireNonNull(dealers, "dealers must not be null");
        dealers.forEach(dealer -> dealer.setTier(resolve(dealer)));
    }
}
