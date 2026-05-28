package com.luckledger.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests the constructive Celestial Fortune (hypergeometric number-match) populator: it must
 * reverse-engineer a 4x4 grid whose winning/player overlap encodes exactly the requested prize, and
 * that grid must round-trip through {@link CelestialFortuneEvaluator} back to the same prize.
 */
class CelestialFortunePopulatorTest {

    private static final long SEED = 20260528L;
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

    private static double prize(int matches) {
        return CelestialFortuneEvaluator.prizeForMatches(matches).doubleValue();
    }

    /** Symbols in the winning row (row 0). */
    private static Set<String> winningNumbers(Grid grid) {
        Set<String> winning = new HashSet<>();
        for (int col = 0; col < DIM; col++) {
            winning.add(grid.getCell(CelestialFortuneEvaluator.WINNING_ROW, col).symbol());
        }
        return winning;
    }

    /** Symbols in the player rows (rows 1-2), row-major. */
    private static List<String> playerNumbers(Grid grid) {
        List<String> player = new ArrayList<>();
        for (int row = CelestialFortuneEvaluator.PLAYER_FIRST_ROW;
                row <= CelestialFortuneEvaluator.PLAYER_LAST_ROW;
                row++) {
            for (int col = 0; col < DIM; col++) {
                player.add(grid.getCell(row, col).symbol());
            }
        }
        return player;
    }

    private static int overlap(Grid grid) {
        Set<String> winning = winningNumbers(grid);
        int matches = 0;
        for (String number : playerNumbers(grid)) {
            if (winning.contains(number)) {
                matches++;
            }
        }
        return matches;
    }

    @Test
    void producesAFourByFourGrid() {
        Grid grid = seeded(SEED).populate(GridSize.FOUR, prize(0), pool());

        assertThat(grid.size()).isEqualTo(GridSize.FOUR);
    }

    @Test
    void loserGridHasZeroOverlapAndPaysNothing() {
        Grid grid = seeded(SEED).populate(GridSize.FOUR, 0.0, pool());

        assertThat(overlap(grid)).isZero();

        EvaluationResult result = evaluator.evaluate(grid);
        assertThat(result.isWinner()).isFalse();
        assertThat(result.prizeAmount()).isEqualByComparingTo("0");
    }

    @Test
    void twoDollarWinnerHasExactlyTwoMatchesAndRoundTrips() {
        Grid grid = seeded(SEED).populate(GridSize.FOUR, prize(2), pool());

        assertThat(overlap(grid)).isEqualTo(2);
        assertThat(evaluator.evaluate(grid).prizeAmount()).isEqualByComparingTo("2");
    }

    @Test
    void twentyDollarWinnerHasExactlyThreeMatchesAndRoundTrips() {
        Grid grid = seeded(SEED).populate(GridSize.FOUR, prize(3), pool());

        assertThat(overlap(grid)).isEqualTo(3);
        assertThat(evaluator.evaluate(grid).prizeAmount()).isEqualByComparingTo("20");
    }

    @Test
    void topPrizeWinnerHasAllFourMatchesAndRoundTrips() {
        Grid grid = seeded(SEED).populate(GridSize.FOUR, prize(4), pool());

        assertThat(overlap(grid)).isEqualTo(4);
        assertThat(evaluator.evaluate(grid).prizeAmount()).isEqualByComparingTo("740");
    }

    @Test
    void everyCellHoldsANumberFromThePoolAndTheDecoyNeverCollidesWithAZone() {
        // A winner necessarily repeats its matched numbers across the winning and player rows (that
        // is what "your number matches a winning number" means), so the grid is not 16 distinct
        // numbers. The legitimate invariants: every symbol is from the pool, each zone is internally
        // distinct, and the inert decoy row shares nothing with either zone.
        Grid grid = seeded(SEED).populate(GridSize.FOUR, prize(3), pool());

        Set<String> poolSet = new HashSet<>(pool());
        for (Cell cell : grid.getAllCells()) {
            assertThat(poolSet).as("symbol %s must come from the pool", cell.symbol()).contains(cell.symbol());
        }

        Set<String> zoneNumbers = new HashSet<>(winningNumbers(grid));
        zoneNumbers.addAll(playerNumbers(grid));
        for (int col = 0; col < DIM; col++) {
            String decoy = grid.getCell(CelestialFortuneEvaluator.DECOY_ROW, col).symbol();
            assertThat(zoneNumbers)
                    .as("decoy %s must not collide with the winning or player numbers", decoy)
                    .doesNotContain(decoy);
        }
    }

    @Test
    void winningRowHasFourDistinctNumbersAndPlayerRowsHaveEightDistinct() {
        Grid grid = seeded(SEED).populate(GridSize.FOUR, prize(2), pool());

        assertThat(winningNumbers(grid)).hasSize(CelestialFortuneEvaluator.WINNING_NUMBER_COUNT);
        assertThat(new HashSet<>(playerNumbers(grid))).hasSize(CelestialFortuneEvaluator.PLAYER_NUMBER_COUNT);
    }

    @Test
    void sameSeedProducesByteIdenticalLayouts() {
        Grid first = seeded(SEED).populate(GridSize.FOUR, prize(3), pool());
        Grid second = seeded(SEED).populate(GridSize.FOUR, prize(3), pool());

        for (int row = 0; row < DIM; row++) {
            for (int col = 0; col < DIM; col++) {
                assertThat(second.getCell(row, col).symbol())
                        .as("cell (%d,%d) must match under the same seed", row, col)
                        .isEqualTo(first.getCell(row, col).symbol());
            }
        }
    }

    @Test
    void roundTripsForEveryPrizeTierAcrossManySeeds() {
        int[] tierMatches = {0, 2, 3, 4};
        for (int matches : tierMatches) {
            for (long seed = 0; seed < 500; seed++) {
                Grid grid = seeded(seed).populate(GridSize.FOUR, prize(matches), pool());
                EvaluationResult result = evaluator.evaluate(grid);
                assertThat(result.prizeAmount())
                        .as("seed %d, target %d matches", seed, matches)
                        .isEqualByComparingTo(CelestialFortuneEvaluator.prizeForMatches(matches));
            }
        }
    }

    @Test
    void tenThousandLoserGridsNeverEvaluateAsWinners() {
        CelestialFortunePopulator populator = seeded(SEED);
        for (int trial = 0; trial < 10_000; trial++) {
            Grid grid = populator.populate(GridSize.FOUR, 0.0, pool());
            assertThat(evaluator.evaluate(grid).isWinner())
                    .as("loser ticket must never win, trial %d", trial)
                    .isFalse();
        }
    }

    @Test
    void matchedPlayerCellsAreScatteredAcrossAllEightPositions() {
        // The player numbers are shuffled, so over many two-match winners every one of the eight
        // player cells should hold a match at least once — proving matches are not pinned to the
        // first k cells.
        CelestialFortunePopulator populator = seeded(SEED);
        Set<Integer> positionsThatEverMatched = new HashSet<>();
        for (int trial = 0; trial < 2_000; trial++) {
            Grid grid = populator.populate(GridSize.FOUR, prize(2), pool());
            Set<String> winning = winningNumbers(grid);
            List<String> player = playerNumbers(grid);
            for (int i = 0; i < player.size(); i++) {
                if (winning.contains(player.get(i))) {
                    positionsThatEverMatched.add(i);
                }
            }
        }
        assertThat(positionsThatEverMatched).hasSize(CelestialFortuneEvaluator.PLAYER_NUMBER_COUNT);
    }

    @Test
    void rejectsGridSizesOtherThanFour() {
        CelestialFortunePopulator populator = seeded(SEED);

        assertThatThrownBy(() -> populator.populate(GridSize.THREE, 0.0, pool()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> populator.populate(GridSize.FIVE, 0.0, pool()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPrizeAmountsOutsideTheCalibratedLadder() {
        CelestialFortunePopulator populator = seeded(SEED);

        assertThatThrownBy(() -> populator.populate(GridSize.FOUR, 5.0, pool()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> populator.populate(GridSize.FOUR, 740.01, pool()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePrizeAmounts() {
        CelestialFortunePopulator populator = seeded(SEED);

        assertThatThrownBy(() -> populator.populate(GridSize.FOUR, -1.0, pool()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPoolsSmallerThanTheNumberPool() {
        CelestialFortunePopulator populator = seeded(SEED);
        List<String> tooFew = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            tooFew.add(String.valueOf(i));
        }

        assertThatThrownBy(() -> populator.populate(GridSize.FOUR, 0.0, tooFew))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullArguments() {
        CelestialFortunePopulator populator = seeded(SEED);

        assertThatThrownBy(() -> populator.populate(null, 0.0, pool()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> populator.populate(GridSize.FOUR, 0.0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultConstructorProducesValidRoundTrippingGrids() {
        Grid grid = new CelestialFortunePopulator().populate(GridSize.FOUR, prize(4), pool());

        assertThat(evaluator.evaluate(grid).prizeAmount()).isEqualByComparingTo("740");
    }
}
