package com.luckledger.mechanic;

import static com.luckledger.mechanic.DemonSealEvaluator.BROKEN_SEAL;
import static com.luckledger.mechanic.DemonSealEvaluator.GOLD_SEAL;
import static com.luckledger.mechanic.DemonSealEvaluator.SEAL_COUNT;
import static com.luckledger.mechanic.DemonSealEvaluator.SILVER_SEAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DemonSealEvaluatorTest {

    private static final String FILLER = "BLANK";
    private static final BigDecimal TICKET_PRICE = new BigDecimal("5");

    private static final double P_GOLD = 0.12;
    private static final double P_SILVER = 0.40;
    private static final double P_BROKEN = 0.48;

    private final DemonSealEvaluator evaluator = new DemonSealEvaluator();

    // --- helpers --------------------------------------------------------------

    /**
     * Builds a 3x3 grid holding exactly SEAL_COUNT seals (the rest filler), in the order given.
     * The evaluator is grid-shape agnostic: it scores by symbol, so filler cells are ignored.
     */
    private static Grid sealGrid(List<String> seals) {
        if (seals.size() != SEAL_COUNT) {
            throw new IllegalArgumentException("expected " + SEAL_COUNT + " seals, was " + seals.size());
        }
        int dim = GridSize.THREE.dimension();
        Cell[][] cells = new Cell[dim][dim];
        for (int i = 0; i < dim * dim; i++) {
            int row = i / dim;
            int col = i % dim;
            String symbol = i < SEAL_COUNT ? seals.get(i) : FILLER;
            cells[row][col] = new Cell(new Position(row, col), symbol, 0.0);
        }
        return new Grid(GridSize.THREE, cells);
    }

    /** Constructs a 6-seal layout with the given composition (constructive — no reject/retry). */
    private static List<String> seals(int gold, int silver, int broken) {
        if (gold + silver + broken != SEAL_COUNT) {
            throw new IllegalArgumentException("seal counts must sum to " + SEAL_COUNT);
        }
        List<String> list = new ArrayList<>(SEAL_COUNT);
        for (int i = 0; i < gold; i++) {
            list.add(GOLD_SEAL);
        }
        for (int i = 0; i < silver; i++) {
            list.add(SILVER_SEAL);
        }
        for (int i = 0; i < broken; i++) {
            list.add(BROKEN_SEAL);
        }
        return list;
    }

    /** Fisher-Yates shuffle with a seeded RNG, per project RNG rules. */
    private static void fisherYates(List<String> list, Random rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            String tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    /** Smallest gold-heavy composition that yields exactly the target points T. */
    private static List<String> sealsForPoints(int target) {
        for (int gold = 0; gold <= SEAL_COUNT; gold++) {
            int silver = target - 2 * gold;
            if (silver >= 0 && gold + silver <= SEAL_COUNT) {
                return seals(gold, silver, SEAL_COUNT - gold - silver);
            }
        }
        throw new IllegalArgumentException("unreachable points: " + target);
    }

    /** Exact trinomial PMF of T = 2G + S over SEAL_COUNT seals. */
    private static double[] theoreticalPmf() {
        double[] pmf = new double[2 * SEAL_COUNT + 1];
        for (int gold = 0; gold <= SEAL_COUNT; gold++) {
            for (int silver = 0; silver + gold <= SEAL_COUNT; silver++) {
                int broken = SEAL_COUNT - gold - silver;
                double coeff = factorial(SEAL_COUNT) / (factorial(gold) * factorial(silver) * factorial(broken));
                double prob = coeff
                        * Math.pow(P_GOLD, gold)
                        * Math.pow(P_SILVER, silver)
                        * Math.pow(P_BROKEN, broken);
                pmf[2 * gold + silver] += prob;
            }
        }
        return pmf;
    }

    private static double factorial(int n) {
        double f = 1.0;
        for (int i = 2; i <= n; i++) {
            f *= i;
        }
        return f;
    }

    /** Draws one trinomial seal layout using the seeded RNG. */
    private static List<String> randomSeals(Random rng) {
        List<String> list = new ArrayList<>(SEAL_COUNT);
        for (int i = 0; i < SEAL_COUNT; i++) {
            double u = rng.nextDouble();
            if (u < P_GOLD) {
                list.add(GOLD_SEAL);
            } else if (u < P_GOLD + P_SILVER) {
                list.add(SILVER_SEAL);
            } else {
                list.add(BROKEN_SEAL);
            }
        }
        return list;
    }

    // --- 1. Deterministic: known layout -> exact result -----------------------

    @Test
    void deterministicLayoutProducesExactResult() {
        // g=2, s=2, b=2 -> T = 2*2 + 2 = 6 -> Sealed-M ($10), a winner.
        Grid grid = sealGrid(List.of(GOLD_SEAL, SILVER_SEAL, BROKEN_SEAL, GOLD_SEAL, SILVER_SEAL, BROKEN_SEAL));

        EvaluationResult result = evaluator.evaluate(grid);

        assertThat(result.isWinner()).isTrue();
        assertThat(result.prizeAmount()).isEqualByComparingTo(new BigDecimal("10"));
        // Scoring seals (GOLD + SILVER) in row-major order.
        assertThat(result.winningPositions())
                .containsExactly(
                        new Position(0, 0), new Position(0, 1), new Position(1, 0), new Position(1, 1));
        assertThat(result.matchDetails())
                .containsEntry(GOLD_SEAL, 2)
                .containsEntry(SILVER_SEAL, 2)
                .containsEntry(BROKEN_SEAL, 2);
    }

    // --- 2. Tier mapping across the full point range --------------------------

    @Test
    void pointsMapToCalibratedPrizeTiers() {
        assertThat(DemonSealEvaluator.prizeForPoints(0)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(DemonSealEvaluator.prizeForPoints(1)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(DemonSealEvaluator.prizeForPoints(2)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(DemonSealEvaluator.prizeForPoints(3)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(DemonSealEvaluator.prizeForPoints(4)).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(DemonSealEvaluator.prizeForPoints(5)).isEqualByComparingTo(new BigDecimal("4"));
        assertThat(DemonSealEvaluator.prizeForPoints(6)).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(DemonSealEvaluator.prizeForPoints(7)).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(DemonSealEvaluator.prizeForPoints(8)).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(DemonSealEvaluator.prizeForPoints(9)).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(DemonSealEvaluator.prizeForPoints(10)).isEqualByComparingTo(new BigDecimal("2500"));
        assertThat(DemonSealEvaluator.prizeForPoints(11)).isEqualByComparingTo(new BigDecimal("2500"));
        assertThat(DemonSealEvaluator.prizeForPoints(12)).isEqualByComparingTo(new BigDecimal("25000"));
    }

    @Test
    void onlyFourPointsOrMoreIsAWinner() {
        for (int t = 0; t <= 2 * SEAL_COUNT; t++) {
            Grid grid = sealGrid(sealsForPoints(t));
            EvaluationResult result = evaluator.evaluate(grid);
            assertThat(result.isWinner()).isEqualTo(t >= 4);
            if (!result.isWinner()) {
                assertThat(result.winningPositions()).isEmpty();
                assertThat(result.prizeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            }
        }
    }

    // --- 3. Round-trip: construct for a target T, shuffle, evaluate ------------

    @Test
    void roundTripGenerateThenEvaluateMatchesPrize() {
        Random rng = new Random(20260528L);
        for (int t = 0; t <= 2 * SEAL_COUNT; t++) {
            List<String> layout = sealsForPoints(t);
            fisherYates(layout, rng);
            EvaluationResult result = evaluator.evaluate(sealGrid(layout));
            assertThat(result.prizeAmount()).isEqualByComparingTo(DemonSealEvaluator.prizeForPoints(t));
            assertThat(result.isWinner()).isEqualTo(t >= 4);
        }
    }

    // --- 4. False positives: 10,000 losers evaluate to zero winners -----------

    @Test
    void tenThousandLosersYieldZeroWinners() {
        Random rng = new Random(424242L);
        // All compositions with 2g + s <= 3 are losers (Escapes).
        int[][] loserCompositions = {
            {0, 0, 6}, {0, 1, 5}, {0, 2, 4}, {0, 3, 3}, {1, 0, 5}, {1, 1, 4}
        };
        int winners = 0;
        for (int i = 0; i < 10_000; i++) {
            int[] comp = loserCompositions[rng.nextInt(loserCompositions.length)];
            List<String> layout = seals(comp[0], comp[1], comp[2]);
            fisherYates(layout, rng);
            EvaluationResult result = evaluator.evaluate(sealGrid(layout));
            if (result.isWinner()) {
                winners++;
            }
        }
        assertThat(winners).isZero();
    }

    // --- 5. Monte Carlo: the evaluator faithfully scores random seal draws ----
    //
    // CALIBRATION CAVEAT: the project's stated per-seal probabilities (gold 0.12 /
    // silver 0.40 / broken 0.48) applied to the calibrated prize ladder yield an RTP
    // far above the 64.4% target (~146%) -- no iid trinomial over those six seals can
    // produce the target, and the published P(T) table is not a valid trinomial (it
    // sums to ~0.858). Hitting 64.4% is the populator's responsibility: it builds a
    // pool with EXACT per-tier counts (a constructed distribution per DESIGN 3.2/3.10),
    // not iid draws. These Monte Carlo tests therefore verify the evaluator scores a
    // KNOWN distribution faithfully (empirical == analytic for the SAME model); the
    // 64.4% calibration is exercised by the populator's tests, not here. The ladder
    // itself -- the evaluator's only calibration surface -- is asserted exactly in
    // pointsMapToCalibratedPrizeTiers.

    @Test
    void monteCarloOutcomeDistributionMatchesTrinomialWithinTwoSigma() {
        final int trials = 1_000_000;
        Random rng = new Random(987654321L);
        double[] pmf = theoreticalPmf();
        assertThat(sum(pmf)).isCloseTo(1.0, within(1e-9));

        long[] counts = new long[2 * SEAL_COUNT + 1];
        for (int i = 0; i < trials; i++) {
            counts[points(evaluator.evaluate(sealGrid(randomSeals(rng))))]++;
        }

        for (int t = 0; t < pmf.length; t++) {
            double expectedCount = pmf[t] * trials;
            if (expectedCount < 30) {
                continue; // too rare for a meaningful normal-approx interval at this N
            }
            double sigma = Math.sqrt(trials * pmf[t] * (1 - pmf[t]));
            assertThat((double) counts[t])
                    .as("count for T=%d (expected ~%.0f)", t, expectedCount)
                    .isCloseTo(expectedCount, within(2 * sigma));
        }
    }

    @Test
    void monteCarloRtpConvergesToTheModelExpectation() {
        final int trials = 1_000_000;
        Random rng = new Random(135792468L);
        double[] pmf = theoreticalPmf();

        BigDecimal totalPaid = BigDecimal.ZERO;
        for (int i = 0; i < trials; i++) {
            totalPaid = totalPaid.add(evaluator.evaluate(sealGrid(randomSeals(rng))).prizeAmount());
        }

        // Empirical RTP converges to the analytic expectation OF THE SAME iid model,
        // confirming the evaluator's scoring is unbiased. Tolerance reflects the heavy
        // jackpot variance at this N (this is not the 64.4% calibration gate -- see note).
        double empiricalRtp = totalPaid.doubleValue() / (trials * TICKET_PRICE.doubleValue());
        assertThat(empiricalRtp).isCloseTo(theoreticalRtp(pmf), within(0.05));
    }

    private static int points(EvaluationResult result) {
        int gold = result.matchDetails().getOrDefault(GOLD_SEAL, 0);
        int silver = result.matchDetails().getOrDefault(SILVER_SEAL, 0);
        return 2 * gold + silver;
    }

    private static double theoreticalRtp(double[] pmf) {
        double expectedPayout = 0.0;
        for (int t = 0; t < pmf.length; t++) {
            expectedPayout += pmf[t] * DemonSealEvaluator.prizeForPoints(t).doubleValue();
        }
        return expectedPayout / TICKET_PRICE.doubleValue();
    }

    private static double sum(double[] values) {
        double total = 0.0;
        for (double v : values) {
            total += v;
        }
        return total;
    }
}
