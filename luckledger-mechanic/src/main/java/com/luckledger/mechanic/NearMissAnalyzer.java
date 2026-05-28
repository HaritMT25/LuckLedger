package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.mechanic.NearMissResult;
import java.util.Objects;

/**
 * Judges how close a losing ticket came to winning, for the educational layer.
 *
 * <p>Reuses the {@link EvaluationResult#matchDetails()} already computed by the {@code WinEvaluator}
 * rather than re-scanning the grid (DRY). For match-count mechanics a win requires reaching a fixed
 * number of matching elements (e.g. three identical symbols for {@code MATCH_3}); a ticket is a
 * near-miss when its best symbol falls exactly one short of that threshold.
 *
 * <p>Point-accumulation mechanics ({@code DEMON_SEAL}) express "closeness" as points short of the
 * win threshold rather than a match count, so they get a dedicated path. Word-accumulation
 * mechanics ({@code CROSSWORD}) still need their own analyzer and are rejected.
 */
public final class NearMissAnalyzer {

    /** Demon Seal seals a win at {@code T = 2*gold + silver >= 4}; a loser at {@code T == 3} is one short. */
    private static final int DEMON_SEAL_WIN_THRESHOLD = 4;

    /**
     * Determines whether a losing {@code result} is a near-miss for the given {@code mechanicType}.
     *
     * <p>A winner is never a near-miss (it is a hit) and is reported with distance {@code 0}.
     *
     * @param result        the evaluation outcome to inspect; never {@code null}
     * @param mechanicType  the mechanic whose win threshold defines "close"; never {@code null}
     * @return the near-miss verdict, distance from a win, and a human-readable description
     * @throws NullPointerException     if {@code result} or {@code mechanicType} is {@code null}
     * @throws IllegalArgumentException if the mechanic has no near-miss model (e.g. {@code CROSSWORD})
     */
    public NearMissResult analyze(EvaluationResult result, MechanicType mechanicType) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(mechanicType, "mechanicType must not be null");

        if (result.isWinner()) {
            return new NearMissResult(false, 0, "Winning ticket — not a near-miss.");
        }
        return switch (mechanicType) {
            case DEMON_SEAL -> analyzePoints(result);
            case CROSSWORD -> throw new IllegalArgumentException(
                    "NearMissAnalyzer has no near-miss model for " + mechanicType
                            + " (word accumulation); it needs mechanic-specific logic.");
            default -> analyzeMatches(result, mechanicType);
        };
    }

    /** Match-count near-miss: a loser one short of the mechanic's required matches. */
    private NearMissResult analyzeMatches(EvaluationResult result, MechanicType mechanicType) {
        int required = matchesRequiredToWin(mechanicType);
        int best = maxMatchCount(result);
        int distance = Math.max(0, required - best);
        boolean nearMiss = distance == 1;

        String description = nearMiss
                ? "Near-miss: %d of %d matches — one short of a win. Engineered to feel like you almost won."
                        .formatted(best, required)
                : "Not a near-miss: %d of %d matches, %d away from a win.".formatted(best, required, distance);

        return new NearMissResult(nearMiss, distance, description);
    }

    /** Point-accumulation near-miss (Demon Seal): a loser one point short of the win threshold. */
    private NearMissResult analyzePoints(EvaluationResult result) {
        int gold = result.matchDetails().getOrDefault(DemonSealEvaluator.GOLD_SEAL, 0);
        int silver = result.matchDetails().getOrDefault(DemonSealEvaluator.SILVER_SEAL, 0);
        int points = DemonSealEvaluator.GOLD_POINTS * gold + DemonSealEvaluator.SILVER_POINTS * silver;
        int distance = Math.max(0, DEMON_SEAL_WIN_THRESHOLD - points);
        boolean nearMiss = distance == 1;

        String description = nearMiss
                ? "Near-miss: %d points — one short of the %d needed to seal the demon. Engineered to feel like you almost won."
                        .formatted(points, DEMON_SEAL_WIN_THRESHOLD)
                : "Not a near-miss: %d points, %d short of a win.".formatted(points, distance);

        return new NearMissResult(nearMiss, distance, description);
    }

    private int maxMatchCount(EvaluationResult result) {
        return result.matchDetails().values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    private int matchesRequiredToWin(MechanicType mechanicType) {
        return switch (mechanicType) {
            case MATCH_3, TIC_TAC_TOE -> 3;
            case BINGO -> 5;
            case CELESTIAL_FORTUNE -> 2;
            case NUMBER_MATCH, KEY_SYMBOL -> 1;
            case DEMON_SEAL, CROSSWORD -> throw new IllegalArgumentException(
                    "NearMissAnalyzer supports match-count mechanics only; "
                            + mechanicType
                            + " accumulates points/words and needs a mechanic-specific analyzer.");
        };
    }
}
