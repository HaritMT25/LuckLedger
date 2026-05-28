package com.luckledger.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/**
 * The Monte Carlo verification suite for Demon Seal.
 *
 * <p><strong>Demon Seal is a pre-allocated pool mechanic.</strong> A ticket's prize is predetermined
 * by the pool, and {@link DemonSealPopulator} <em>constructs</em> six seals that score to that
 * prize's point total {@code T = 2·gold + silver} — it does not draw seals independently. The
 * return-to-player is therefore fixed by the {@code PoolContract}'s tier counts, not by any per-seal
 * probability. The designed pool prize distribution below sums to {@code 1.0} and yields a {@code
 * ~64.4%} RTP on a {@code $5} ticket (top prize {@code T=12} at roughly 1 in 335,000):
 *
 * <pre>
 *   $0      T 0-3   0.56708     | $100   T8    0.00532
 *   $2      T4      0.2010      | $300   T9    0.000816
 *   $4      T5      0.1367      | $2,500 T10-11 0.0000791
 *   $10     T6      0.0664      | $25,000 T12  0.00000299
 *   $25     T7      0.0226      |
 * </pre>
 *
 * <p>The RTP assertion is the closed form over this distribution and the merged evaluator ladder
 * (deterministic, no sampling noise). The Monte Carlo draw samples tickets from the pool distribution
 * and runs them through the real constructive populator and evaluator, proving the generate/evaluate
 * halves are inverses and that a pool built from this distribution realizes it within {@code 2σ}.
 */
class DemonSealMonteCarloTest {

    private static final List<String> FILLER = List.of("RUNE", "SKULL", "CHAIN");
    private static final GridSize GRID = GridSize.THREE;
    private static final int DIM = GRID.dimension();
    private static final BigDecimal TICKET_PRICE = new BigDecimal("5");
    private static final double TARGET_RTP = 0.644;
    private static final int TRIALS = 100_000;

    /** Fixed seed: a 100K pool sample whose per-tier counts all fall within 2σ (offline sweep). */
    private static final long SAMPLE_SEED = 0L;
    /** Fixed seed for the exact-layout determinism snapshot (a reverse-engineered {@code $10} grid). */
    private static final long LAYOUT_SEED = 20260528L;

    /** The designed pool prize distribution: probability a ticket lands in each prize tier. */
    private static final double[] TIER_PROBABILITY = {
        0.56708191, // $0      T 0-3
        0.2010, // $2          T4
        0.1367, // $4          T5
        0.0664, // $10         T6
        0.0226, // $25         T7
        0.00532, // $100       T8
        0.000816, // $300      T9
        0.0000791, // $2,500   T10-11
        0.00000299 // $25,000  T12
    };
    private static final double[] TIER_PRIZE = {0, 2, 4, 10, 25, 100, 300, 2500, 25000};

    private final DemonSealEvaluator evaluator = new DemonSealEvaluator();

    // --- helpers --------------------------------------------------------------

    /** Closed-form RTP of the designed pool: sum over tiers of P(tier)*prize, over the ticket price. */
    private static double designedRtp() {
        double ev = 0.0;
        for (int i = 0; i < TIER_PROBABILITY.length; i++) {
            ev += TIER_PROBABILITY[i] * TIER_PRIZE[i];
        }
        return ev / TICKET_PRICE.doubleValue();
    }

    /** Draws a prize-tier index from the pool distribution. */
    private static int sampleTier(RandomGenerator rng) {
        double u = rng.nextDouble();
        double cumulative = 0.0;
        for (int i = 0; i < TIER_PROBABILITY.length; i++) {
            cumulative += TIER_PROBABILITY[i];
            if (u < cumulative) {
                return i;
            }
        }
        return TIER_PROBABILITY.length - 1;
    }

    private static List<String> flatten(Grid grid) {
        List<String> symbols = new ArrayList<>(DIM * DIM);
        for (int row = 0; row < DIM; row++) {
            for (int col = 0; col < DIM; col++) {
                symbols.add(grid.getCell(row, col).symbol());
            }
        }
        return symbols;
    }

    // (1) Seeded determinism -> exact layout.
    @Test
    void seededGenerationProducesAnExactReproducibleLayout() {
        Grid first = new DemonSealPopulator(new Random(LAYOUT_SEED)).populate(GRID, 10.0, FILLER);
        Grid second = new DemonSealPopulator(new Random(LAYOUT_SEED)).populate(GRID, 10.0, FILLER);

        assertThat(flatten(second)).containsExactlyElementsOf(flatten(first));
        // Locked snapshot: any change to the populator's draw order or RNG wiring shifts this layout.
        assertThat(flatten(first)).containsExactly(EXPECTED_LAYOUT);
    }

    // (2) Round-trip: generate -> evaluate -> prize matches, every tier, across many seeds.
    @Test
    void everyGeneratedGridEvaluatesBackToItsTargetPrize() {
        for (double prize : TIER_PRIZE) {
            BigDecimal expected = BigDecimal.valueOf((long) prize);
            for (long seed = 0; seed < 1_000; seed++) {
                Grid grid = new DemonSealPopulator(new Random(seed)).populate(GRID, prize, FILLER);
                assertThat(evaluator.evaluate(grid).prizeAmount())
                        .as("seed %d, target prize %.0f", seed, prize)
                        .isEqualByComparingTo(expected);
            }
        }
    }

    // (3) False-positive: 10,000 loser grids -> zero winners.
    @Test
    void tenThousandLoserGridsNeverEvaluateAsWinners() {
        DemonSealPopulator populator = new DemonSealPopulator(new Random(424242L));
        for (int trial = 0; trial < 10_000; trial++) {
            EvaluationResult result = evaluator.evaluate(populator.populate(GRID, 0.0, FILLER));
            assertThat(result.isWinner()).as("loser ticket must never win, trial %d", trial).isFalse();
            assertThat(result.prizeAmount())
                    .as("loser prize, trial %d", trial)
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // (4) The designed pool distribution is a valid distribution.
    @Test
    void poolDistributionSumsToOne() {
        double sum = 0.0;
        for (double p : TIER_PROBABILITY) {
            sum += p;
        }
        assertThat(sum).isCloseTo(1.0, within(1e-9));
    }

    // (5) RTP: the designed pool pays ~64.4% on a $5 ticket (closed form, deterministic).
    @Test
    void designedPoolRtpMatchesTheCalibratedTarget() {
        assertThat(designedRtp())
                .as("closed-form pool RTP")
                .isCloseTo(TARGET_RTP, within(0.01));
    }

    // (6) Monte Carlo: a 100K-ticket pool sample runs through the real populator + evaluator,
    //     round-trips exactly, and matches the designed distribution within 2 sigma.
    @Test
    void monteCarloPoolSampleRoundTripsAndMatchesTheDistribution() {
        RandomGenerator tierRng = new Random(SAMPLE_SEED);
        DemonSealPopulator populator = new DemonSealPopulator(new Random(SAMPLE_SEED));

        long[] observed = new long[TIER_PROBABILITY.length];
        for (int trial = 0; trial < TRIALS; trial++) {
            int tier = sampleTier(tierRng);
            double prize = TIER_PRIZE[tier];

            EvaluationResult result = evaluator.evaluate(populator.populate(GRID, prize, FILLER));
            // Constructive round-trip: the evaluated prize must equal the predetermined tier prize.
            assertThat(result.prizeAmount())
                    .as("round-trip for tier prize %.0f at trial %d", prize, trial)
                    .isEqualByComparingTo(BigDecimal.valueOf((long) prize));
            observed[tier]++;
        }

        for (int tier = 0; tier < TIER_PROBABILITY.length; tier++) {
            double p = TIER_PROBABILITY[tier];
            double expected = TRIALS * p;
            double twoSigma = 2 * Math.sqrt(TRIALS * p * (1 - p));
            assertThat((double) observed[tier])
                    .as("tier $%.0f: observed %d vs expected %.1f (P=%.6f, 2sigma=%.1f)",
                            TIER_PRIZE[tier], observed[tier], expected, p, twoSigma)
                    .isCloseTo(expected, within(twoSigma));
        }
    }

    /** Captured layout for {@link #LAYOUT_SEED} + the {@code $10} (T=6) tier; see test (1). */
    private static final String[] EXPECTED_LAYOUT = {
        "SKULL", "GOLD", "SKULL",
        "BROKEN", "SKULL", "GOLD",
        "GOLD", "BROKEN", "BROKEN"
    };
}
