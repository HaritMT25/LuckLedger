package com.luckledger.mechanic;

import static com.luckledger.mechanic.DemonSealPopulator.GOLD_SEAL;
import static com.luckledger.mechanic.DemonSealPopulator.SILVER_SEAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.LoserLayout;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Near-miss engineering tests for {@link DemonSealPopulator}.
 *
 * <p>The win floor is {@code T = 4}. A {@link LoserLayout#CLEAN} {@code $0} loser scores
 * {@code T <= 2}; a {@link LoserLayout#NEAR_MISS} {@code $0} loser scores exactly {@code T = 3} — one
 * short of the floor — yet still pays {@code $0}. Winners ignore the layout flag, so near-miss
 * engineering is RTP-neutral.
 */
class DemonSealNearMissTest {

    private static final List<String> SYMBOL_POOL = List.of("BLANK", "RUNE", "SKULL");

    private final DemonSealEvaluator evaluator = new DemonSealEvaluator();

    private static DemonSealPopulator seeded(long seed) {
        return new DemonSealPopulator(new Random(seed));
    }

    private static int points(Grid grid) {
        return DemonSealPopulator.GOLD_POINTS * grid.getCellsBySymbol(GOLD_SEAL).size()
                + DemonSealPopulator.SILVER_POINTS * grid.getCellsBySymbol(SILVER_SEAL).size();
    }

    @Test
    void nearMissLoserScoresExactlyThreeAndPaysZero() {
        Grid grid = seeded(20260707L).populate(GridSize.THREE, 0.0, SYMBOL_POOL, LoserLayout.NEAR_MISS);

        assertThat(points(grid)).isEqualTo(DemonSealPopulator.NEAR_MISS_POINTS).isEqualTo(3);
        assertThat(evaluator.evaluate(grid).isWinner()).isFalse();
        assertThat(evaluator.evaluate(grid).prizeAmount()).isEqualByComparingTo("0");
    }

    @Test
    void cleanLoserScoresAtMostTwo() {
        DemonSealPopulator populator = seeded(20260707L);
        for (int trial = 0; trial < 2_000; trial++) {
            Grid grid = populator.populate(GridSize.THREE, 0.0, SYMBOL_POOL, LoserLayout.CLEAN);
            assertThat(points(grid))
                    .as("clean loser must stay at or below %d, trial %d",
                            DemonSealPopulator.CLEAN_MAX_LOSER_POINTS, trial)
                    .isLessThanOrEqualTo(DemonSealPopulator.CLEAN_MAX_LOSER_POINTS);
        }
    }

    @Test
    void threeArgOverloadDefaultsToACleanLoser() {
        DemonSealPopulator populator = seeded(13L);
        for (int trial = 0; trial < 2_000; trial++) {
            assertThat(points(populator.populate(GridSize.THREE, 0.0, SYMBOL_POOL)))
                    .as("default (3-arg) loser must be clean, trial %d", trial)
                    .isLessThanOrEqualTo(DemonSealPopulator.CLEAN_MAX_LOSER_POINTS);
        }
    }

    @Test
    void tenThousandNearMissLosersAlwaysScoreThreeAndNeverWin() {
        DemonSealPopulator populator = seeded(99L);
        for (int trial = 0; trial < 10_000; trial++) {
            Grid grid = populator.populate(GridSize.THREE, 0.0, SYMBOL_POOL, LoserLayout.NEAR_MISS);
            assertThat(points(grid)).as("near-miss loser scores three, trial %d", trial).isEqualTo(3);
            assertThat(evaluator.evaluate(grid).isWinner())
                    .as("near-miss loser must never win, trial %d", trial)
                    .isFalse();
        }
    }

    @Test
    void winnersIgnoreTheLayoutFlag() {
        // T=6 pays $10; the prize is identical whichever layout is passed.
        for (LoserLayout layout : LoserLayout.values()) {
            Grid grid = seeded(7L).populate(GridSize.THREE, 10.0, SYMBOL_POOL, layout);
            assertThat(points(grid)).as("winner points under layout %s", layout).isEqualTo(6);
            assertThat(evaluator.evaluate(grid).prizeAmount())
                    .as("winner prize under layout %s", layout)
                    .isEqualByComparingTo("10");
        }
    }
}
