package com.luckledger.api;

import java.util.List;

/**
 * A read-only, education-layer explanation of an already-revealed ticket. It is derived by
 * re-evaluating the ticket's stored grid and <strong>never influences payout</strong> — the reveal
 * always credits the ticket's persisted prize. Fields that do not apply to a given mechanic are
 * {@code null} (for example {@code sealScore} for Celestial Fortune, or {@code matchCount} for Demon
 * Seal).
 *
 * @param matchCount       Celestial: how many of the winning numbers the player matched; else null
 * @param matchesNeeded    Celestial: the matches needed to reach the win floor; else null
 * @param sealScore        Demon: the seal score {@code T = 2·gold + silver}; else null
 * @param gold             Demon: gold seals revealed; else null
 * @param silver           Demon: silver seals revealed; else null
 * @param pointsNeeded     Demon: the seal points needed to reach the win floor; else null
 * @param nearMiss         whether this losing ticket was engineered to land one step from a win
 * @param summary          a short, human-readable sentence in the app's education voice
 * @param matchedPositions the grid cells that "counted" toward the outcome (for highlighting)
 */
public record OutcomeNarrative(
        Integer matchCount,
        Integer matchesNeeded,
        Integer sealScore,
        Integer gold,
        Integer silver,
        Integer pointsNeeded,
        boolean nearMiss,
        String summary,
        List<CellRef> matchedPositions) {

    /** A grid cell reference (row, col) — the placement of a matched/scoring cell. */
    public record CellRef(int row, int col) {}
}
