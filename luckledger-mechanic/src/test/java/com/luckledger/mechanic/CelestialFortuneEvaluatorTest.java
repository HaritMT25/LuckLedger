package com.luckledger.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CelestialFortuneEvaluatorTest {

    private static final int DIM = GridSize.FOUR.dimension();

    private final CelestialFortuneEvaluator evaluator = new CelestialFortuneEvaluator();

    /**
     * Builds a Celestial Fortune grid per the evaluator's layout contract: row 0 holds the four
     * winning numbers, rows 1-2 hold the eight player numbers (row-major), row 3 is inert decoy.
     */
    private static Grid grid(int[] winning, int[] player) {
        return grid(winning, player, new String[] {"decoy0", "decoy1", "decoy2", "decoy3"});
    }

    private static Grid grid(int[] winning, int[] player, String[] decoyRow) {
        if (winning.length != 4) {
            throw new IllegalArgumentException("test grid needs 4 winning numbers");
        }
        if (player.length != 8) {
            throw new IllegalArgumentException("test grid needs 8 player numbers");
        }
        Cell[][] cells = new Cell[DIM][DIM];
        for (int col = 0; col < DIM; col++) {
            cells[0][col] = new Cell(new Position(0, col), String.valueOf(winning[col]), 0.0);
        }
        int p = 0;
        for (int row = 1; row <= 2; row++) {
            for (int col = 0; col < DIM; col++) {
                cells[row][col] = new Cell(new Position(row, col), String.valueOf(player[p++]), 0.0);
            }
        }
        for (int col = 0; col < DIM; col++) {
            cells[3][col] = new Cell(new Position(3, col), decoyRow[col], 0.0);
        }
        return new Grid(GridSize.FOUR, cells);
    }

    /** Winner grid with exactly k of the player numbers drawn from the winning set. */
    private static Grid gridWithOverlap(int k) {
        int[] winning = {1, 2, 3, 4};
        int[] player = new int[8];
        for (int i = 0; i < k; i++) {
            player[i] = winning[i]; // matches
        }
        for (int i = k; i < 8; i++) {
            player[i] = 11 + (i - k); // complement (11..), disjoint from winning
        }
        return grid(winning, player);
    }

    @Test
    void zeroOverlapIsALoserPayingNothing() {
        EvaluationResult result = evaluator.evaluate(gridWithOverlap(0));

        assertThat(result.isWinner()).isFalse();
        assertThat(result.prizeAmount()).isEqualByComparingTo("0");
        assertThat(result.winningPositions()).isEmpty();
        assertThat(result.matchDetails()).containsEntry(CelestialFortuneEvaluator.MATCH_COUNT_KEY, 0);
    }

    @Test
    void oneOverlapIsALoserButRecordsTheMatchCountForNearMissAnalysis() {
        EvaluationResult result = evaluator.evaluate(gridWithOverlap(1));

        assertThat(result.isWinner()).isFalse();
        assertThat(result.prizeAmount()).isEqualByComparingTo("0");
        assertThat(result.winningPositions()).isEmpty();
        // NearMissAnalyzer reads max(matchDetails.values()) as k against a threshold of 2,
        // so a single-match loser must surface k=1 to register as a near-miss.
        assertThat(result.matchDetails().values().stream().mapToInt(Integer::intValue).max().orElse(0))
                .isEqualTo(1);
    }

    @Test
    void twoMatchesWinTwoDollars() {
        EvaluationResult result = evaluator.evaluate(gridWithOverlap(2));

        assertThat(result.isWinner()).isTrue();
        assertThat(result.prizeAmount()).isEqualByComparingTo("2");
        assertThat(result.winningPositions()).hasSize(2);
        assertThat(result.matchDetails()).containsEntry(CelestialFortuneEvaluator.MATCH_COUNT_KEY, 2);
    }

    @Test
    void threeMatchesWinTwentyDollars() {
        EvaluationResult result = evaluator.evaluate(gridWithOverlap(3));

        assertThat(result.isWinner()).isTrue();
        assertThat(result.prizeAmount()).isEqualByComparingTo("20");
        assertThat(result.winningPositions()).hasSize(3);
        assertThat(result.matchDetails()).containsEntry(CelestialFortuneEvaluator.MATCH_COUNT_KEY, 3);
    }

    @Test
    void fourMatchesWinTheTopPrize() {
        EvaluationResult result = evaluator.evaluate(gridWithOverlap(4));

        assertThat(result.isWinner()).isTrue();
        assertThat(result.prizeAmount()).isEqualByComparingTo("740");
        assertThat(result.winningPositions()).hasSize(4);
        assertThat(result.matchDetails()).containsEntry(CelestialFortuneEvaluator.MATCH_COUNT_KEY, 4);
    }

    @Test
    void winningPositionsPointAtTheMatchedPlayerCells() {
        // winning {5,6,7,8}; player row 1 = {5,6,99,98}, row 2 = {97,7,96,95} -> matches at (1,0),(1,1),(2,1)
        int[] winning = {5, 6, 7, 8};
        int[] player = {5, 6, 99, 98, 97, 7, 96, 95};

        EvaluationResult result = evaluator.evaluate(grid(winning, player));

        assertThat(result.isWinner()).isTrue();
        assertThat(result.prizeAmount()).isEqualByComparingTo("20");
        assertThat(result.winningPositions())
                .containsExactlyInAnyOrder(new Position(1, 0), new Position(1, 1), new Position(2, 1));
    }

    @Test
    void decoyRowIsIgnoredEvenWhenItRepeatsAWinningNumber() {
        // k=0 loser, but the decoy row echoes every winning number. The decoy row must not count.
        int[] winning = {1, 2, 3, 4};
        int[] player = {11, 12, 13, 14, 15, 16, 17, 18};
        String[] decoy = {"1", "2", "3", "4"};

        EvaluationResult result = evaluator.evaluate(grid(winning, player, decoy));

        assertThat(result.isWinner()).isFalse();
        assertThat(result.prizeAmount()).isEqualByComparingTo("0");
        assertThat(result.matchDetails()).containsEntry(CelestialFortuneEvaluator.MATCH_COUNT_KEY, 0);
    }

    @Test
    void matchesAreCountedRegardlessOfPlayerCellOrdering() {
        // Same two matches, scattered into different cells, still yields k=2.
        EvaluationResult a = evaluator.evaluate(grid(new int[] {1, 2, 3, 4}, new int[] {1, 11, 12, 2, 13, 14, 15, 16}));
        EvaluationResult b = evaluator.evaluate(grid(new int[] {1, 2, 3, 4}, new int[] {11, 12, 13, 14, 15, 1, 16, 2}));

        assertThat(a.prizeAmount()).isEqualByComparingTo("2");
        assertThat(b.prizeAmount()).isEqualByComparingTo("2");
    }

    @Test
    void rejectsGridsThatAreNotFourByFour() {
        Cell[][] cells = new Cell[3][3];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                cells[row][col] = new Cell(new Position(row, col), "1", 0.0);
            }
        }
        Grid threeByThree = new Grid(GridSize.THREE, cells);

        assertThatThrownBy(() -> evaluator.evaluate(threeByThree))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSingleInstanceEvaluatesManyGridsIndependently() {
        // WinEvaluator contract: stateless, reused at generation-time verification and play time.
        assertThat(evaluator.evaluate(gridWithOverlap(4)).isWinner()).isTrue();
        assertThat(evaluator.evaluate(gridWithOverlap(0)).isWinner()).isFalse();
        assertThat(evaluator.evaluate(gridWithOverlap(2)).prizeAmount()).isEqualByComparingTo("2");
    }

    @Test
    void tenThousandDisjointLoserGridsNeverEvaluateAsWinners() {
        Random random = new Random(20260528L);
        int[] pool = new int[30];
        for (int i = 0; i < 30; i++) {
            pool[i] = i + 1;
        }
        for (int trial = 0; trial < 10_000; trial++) {
            shuffle(pool, random);
            int[] winning = {pool[0], pool[1], pool[2], pool[3]};
            int[] player = {pool[4], pool[5], pool[6], pool[7], pool[8], pool[9], pool[10], pool[11]};

            EvaluationResult result = evaluator.evaluate(grid(winning, player));

            assertThat(result.isWinner())
                    .as("disjoint winning/player sets must never win, trial %d", trial)
                    .isFalse();
            assertThat(result.prizeAmount()).isEqualByComparingTo("0");
        }
    }

    @Test
    void prizeMappingMatchesTheCalibratedReturnToPlayer() {
        // Exact hypergeometric weights P(k) = C(8,k)*C(22,4-k)/C(30,4) over the prize table must
        // land on the 65.2% RTP calibration for a $5 ticket.
        double ticketPrice = 5.0;
        double total = combinations(30, 4);
        double expectedPayout = 0.0;
        for (int k = 0; k <= 4; k++) {
            double pk = combinations(8, k) * combinations(22, 4 - k) / total;
            double prize = CelestialFortuneEvaluator.prizeForMatches(k).doubleValue();
            expectedPayout += pk * prize;
        }

        double rtp = expectedPayout / ticketPrice;

        assertThat(rtp).isCloseTo(0.652, within(0.002));
    }

    @Test
    void prizeForMatchesRejectsImpossibleOverlapCounts() {
        assertThatThrownBy(() -> CelestialFortuneEvaluator.prizeForMatches(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CelestialFortuneEvaluator.prizeForMatches(5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void shuffle(int[] array, Random random) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    private static double combinations(int n, int r) {
        if (r < 0 || r > n) {
            return 0.0;
        }
        double result = 1.0;
        for (int i = 0; i < r; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }
}
