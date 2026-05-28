package com.luckledger.domain.generation.verification;

import java.util.Map;
import java.util.Objects;

/**
 * Informational summary of how close the losing tickets in a generated batch came to winning.
 *
 * <p>This is <strong>not</strong> a pass/fail check — near-misses are expected, and in realistic
 * mode deliberately engineered — so it reports data rather than a verdict: the loser count, how many
 * of those were near-misses, the resulting rate, and the full distribution of "distance to a win".
 *
 * @param totalLosers number of losing tickets examined; {@code >= 0}
 * @param nearMissCount losers that came within the near-miss threshold; {@code 0..totalLosers}
 * @param nearMissRate {@code nearMissCount / totalLosers} (or {@code 0} when there are no losers);
 *     in {@code [0.0, 1.0]}
 * @param distribution distance-to-win → count of losers at that distance; never {@code null}, keys
 *     and counts {@code >= 0}. Held as an unmodifiable copy.
 */
public record NearMissReport(
        int totalLosers, int nearMissCount, double nearMissRate, Map<Integer, Integer> distribution) {

    public NearMissReport {
        Objects.requireNonNull(distribution, "distribution must not be null");
        if (totalLosers < 0) {
            throw new IllegalArgumentException("totalLosers must be >= 0, was " + totalLosers);
        }
        if (nearMissCount < 0 || nearMissCount > totalLosers) {
            throw new IllegalArgumentException(
                    "nearMissCount must be in [0, " + totalLosers + "], was " + nearMissCount);
        }
        if (nearMissRate < 0.0 || nearMissRate > 1.0) {
            throw new IllegalArgumentException("nearMissRate must be in [0.0, 1.0], was " + nearMissRate);
        }
        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 0) {
                throw new IllegalArgumentException("distribution distance must be >= 0, was " + entry.getKey());
            }
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("distribution count must be >= 0, was " + entry.getValue());
            }
        }
        distribution = Map.copyOf(distribution);
    }
}
