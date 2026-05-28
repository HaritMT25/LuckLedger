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
 * <p>Point- and word-accumulation mechanics ({@code DEMON_SEAL}, {@code CROSSWORD}) do not express
 * "closeness" as a count of identical matches, so this analyzer rejects them — they need their own
 * mechanic-specific near-miss logic.
 */
public final class NearMissAnalyzer {

    /**
     * Determines whether a losing {@code result} is a near-miss for the given {@code mechanicType}.
     *
     * <p>A winner is never a near-miss (it is a hit) and is reported with distance {@code 0}.
     *
     * @param result        the evaluation outcome to inspect; never {@code null}
     * @param mechanicType  the mechanic whose win threshold defines "close"; never {@code null}
     * @return the near-miss verdict, distance from a win, and a human-readable description
     * @throws NullPointerException     if {@code result} or {@code mechanicType} is {@code null}
     * @throws IllegalArgumentException if {@code mechanicType} is not a match-count mechanic
     */
    public NearMissResult analyze(EvaluationResult result, MechanicType mechanicType) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(mechanicType, "mechanicType must not be null");

        if (result.isWinner()) {
            return new NearMissResult(false, 0, "Winning ticket — not a near-miss.");
        }

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
