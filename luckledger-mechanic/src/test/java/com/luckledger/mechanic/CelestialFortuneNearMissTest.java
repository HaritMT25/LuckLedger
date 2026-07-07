package com.luckledger.mechanic;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.LoserLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Near-miss engineering tests for {@link CelestialFortunePopulator}.
 *
 * <p>The win floor is two matches ({@code $2}). A {@link LoserLayout#CLEAN} {@code $0} loser carries
 * zero overlap (an obvious miss); a {@link LoserLayout#NEAR_MISS} {@code $0} loser carries exactly one
 * matching number — one short of the floor — yet still evaluates to {@code $0}. Winners ignore the
 * layout flag entirely, so no engineered near-miss can move a cent of RTP.
 */
class CelestialFortuneNearMissTest {

    private static final int DIM = GridSize.FOUR.dimension();

    private final CelestialFortuneEvaluator evaluator = new CelestialFortuneEvaluator();

    private static List<String> pool() {
        List<String> numbers = new ArrayList<>();
        for (int i = 1; i <= CelestialFortuneEvaluator.NUMBER_POOL_SIZE; i++) {
            numbers.add(String.valueOf(i));
        }
        return numbers;
    }

    private static CelestialFortunePopulator seeded(long seed) {
        return new CelestialFortunePopulator(new GridUtils(new Random(seed)));
    }

    /** Distinct-match count the evaluator scores the grid at. */
    private int matchCount(Grid grid) {
        return evaluator.evaluate(grid).matchDetails().get(CelestialFortuneEvaluator.MATCH_COUNT_KEY);
    }

    @Test
    void nearMissLoserPaysZeroButHasExactlyOneMatch() {
        Grid grid = seeded(20260707L).populate(GridSize.FOUR, 0.0, pool(), LoserLayout.NEAR_MISS);

        EvaluationResult result = evaluator.evaluate(grid);
        assertThat(result.isWinner()).isFalse();
        assertThat(result.prizeAmount()).isEqualByComparingTo("0");
        assertThat(matchCount(grid)).isEqualTo(CelestialFortunePopulator.NEAR_MISS_OVERLAP).isEqualTo(1);
    }

    @Test
    void cleanLoserHasZeroMatches() {
        Grid grid = seeded(20260707L).populate(GridSize.FOUR, 0.0, pool(), LoserLayout.CLEAN);

        assertThat(evaluator.evaluate(grid).prizeAmount()).isEqualByComparingTo("0");
        assertThat(matchCount(grid)).isZero();
    }

    @Test
    void threeArgOverloadDefaultsToACleanLoser() {
        Grid grid = seeded(20260707L).populate(GridSize.FOUR, 0.0, pool());

        assertThat(matchCount(grid)).isZero();
    }

    @Test
    void tenThousandNearMissLosersNeverWinAndAlwaysShowExactlyOneMatch() {
        CelestialFortunePopulator populator = seeded(99L);
        for (int trial = 0; trial < 10_000; trial++) {
            Grid grid = populator.populate(GridSize.FOUR, 0.0, pool(), LoserLayout.NEAR_MISS);
            assertThat(evaluator.evaluate(grid).isWinner())
                    .as("near-miss loser must never win, trial %d", trial)
                    .isFalse();
            assertThat(matchCount(grid))
                    .as("near-miss loser must show exactly one match, trial %d", trial)
                    .isEqualTo(1);
        }
    }

    @Test
    void winnersAreUnaffectedByTheLayoutFlag() {
        double twoMatchPrize = CelestialFortuneEvaluator.prizeForMatches(2).doubleValue();
        for (LoserLayout layout : LoserLayout.values()) {
            Grid grid = seeded(7L).populate(GridSize.FOUR, twoMatchPrize, pool(), layout);
            assertThat(evaluator.evaluate(grid).prizeAmount())
                    .as("winner prize under layout %s", layout)
                    .isEqualByComparingTo("2");
            assertThat(matchCount(grid)).as("winner matches under layout %s", layout).isEqualTo(2);
        }
    }

    @Test
    void aWinnerGridIsByteIdenticalAcrossLayoutsUnderTheSameSeed() {
        // The layout flag only affects the $0 branch; for a winner the populator consumes RNG
        // identically, so the same seed yields the same grid regardless of the flag passed.
        double topPrize = CelestialFortuneEvaluator.prizeForMatches(4).doubleValue();
        Grid clean = seeded(42L).populate(GridSize.FOUR, topPrize, pool(), LoserLayout.CLEAN);
        Grid nearMiss = seeded(42L).populate(GridSize.FOUR, topPrize, pool(), LoserLayout.NEAR_MISS);

        for (int row = 0; row < DIM; row++) {
            for (int col = 0; col < DIM; col++) {
                assertThat(nearMiss.getCell(row, col).symbol())
                        .as("winner cell (%d,%d) must not depend on the layout flag", row, col)
                        .isEqualTo(clean.getCell(row, col).symbol());
            }
        }
    }
}
