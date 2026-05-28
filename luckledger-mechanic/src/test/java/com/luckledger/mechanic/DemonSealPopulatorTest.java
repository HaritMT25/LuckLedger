package com.luckledger.mechanic;

import static com.luckledger.mechanic.DemonSealPopulator.BROKEN_SEAL;
import static com.luckledger.mechanic.DemonSealPopulator.GOLD_SEAL;
import static com.luckledger.mechanic.DemonSealPopulator.SEAL_COUNT;
import static com.luckledger.mechanic.DemonSealPopulator.SILVER_SEAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Contract tests for {@link DemonSealPopulator}.
 *
 * <p>The populator is the inverse of {@code DemonSealEvaluator}: given a predetermined prize it
 * reverse-engineers a six-seal layout (T = 2·gold + silver) that scores back to that prize. The
 * evaluator already proves its scoring; these tests therefore re-derive T from the produced grid by
 * counting seal symbols (the same rule the evaluator applies), keeping this bead independent of the
 * evaluator. Pool-level RTP calibration is exercised by the dedicated Monte Carlo suite, not here:
 * the populator realizes whatever exact per-tier counts the pool dictates, it does not sample.
 */
class DemonSealPopulatorTest {

    private static final String[] FILLER = {"BLANK", "RUNE", "SKULL"};
    private static final List<String> SYMBOL_POOL = List.of(FILLER);
    private static final Set<String> SEAL_SYMBOLS = Set.of(GOLD_SEAL, SILVER_SEAL, BROKEN_SEAL);

    /** Every distinct prize on the calibrated Demon Seal ladder. */
    private static final BigDecimal[] PRIZE_TIERS = {
        BigDecimal.ZERO,
        new BigDecimal("2"),
        new BigDecimal("4"),
        new BigDecimal("10"),
        new BigDecimal("25"),
        new BigDecimal("100"),
        new BigDecimal("300"),
        new BigDecimal("2500"),
        new BigDecimal("25000"),
    };

    // --- helpers --------------------------------------------------------------

    private static DemonSealPopulator seeded(long seed) {
        return new DemonSealPopulator(new Random(seed));
    }

    private static int count(Grid grid, String symbol) {
        return grid.getCellsBySymbol(symbol).size();
    }

    private static int sealPoints(Grid grid) {
        return DemonSealPopulator.GOLD_POINTS * count(grid, GOLD_SEAL)
                + DemonSealPopulator.SILVER_POINTS * count(grid, SILVER_SEAL);
    }

    private static int sealTotal(Grid grid) {
        return count(grid, GOLD_SEAL) + count(grid, SILVER_SEAL) + count(grid, BROKEN_SEAL);
    }

    /** The design's calibrated point→prize ladder, hardcoded here as an independent oracle. */
    private static BigDecimal expectedPrizeForPoints(int t) {
        return switch (t) {
            case 0, 1, 2, 3 -> BigDecimal.ZERO;
            case 4 -> new BigDecimal("2");
            case 5 -> new BigDecimal("4");
            case 6 -> new BigDecimal("10");
            case 7 -> new BigDecimal("25");
            case 8 -> new BigDecimal("100");
            case 9 -> new BigDecimal("300");
            case 10, 11 -> new BigDecimal("2500");
            default -> new BigDecimal("25000");
        };
    }

    // --- 1. Deterministic: same seed -> identical layout ----------------------

    @Test
    void sameSeedProducesIdenticalGrid() {
        Grid a = seeded(20260528L).populate(GridSize.THREE, 10.0, SYMBOL_POOL);
        Grid b = seeded(20260528L).populate(GridSize.THREE, 10.0, SYMBOL_POOL);

        int dim = GridSize.THREE.dimension();
        for (int row = 0; row < dim; row++) {
            for (int col = 0; col < dim; col++) {
                Cell ca = a.getCell(row, col);
                Cell cb = b.getCell(row, col);
                assertThat(ca.symbol()).isEqualTo(cb.symbol());
                assertThat(ca.prizeValue()).isEqualTo(cb.prizeValue());
            }
        }
    }

    @Test
    void differentSeedsCanProduceDifferentLayouts() {
        // Same prize, but the seal placement / composition is randomized, so layouts vary.
        Grid a = seeded(1L).populate(GridSize.THREE, 0.0, SYMBOL_POOL);
        Grid b = seeded(2L).populate(GridSize.THREE, 0.0, SYMBOL_POOL);
        // Both are valid $0 layouts; we only assert the seed actually drives variety somewhere.
        boolean differs = false;
        for (int i = 0; i < a.getAllCells().size(); i++) {
            if (!a.getAllCells().get(i).symbol().equals(b.getAllCells().get(i).symbol())) {
                differs = true;
                break;
            }
        }
        assertThat(differs).isTrue();
    }

    // --- 2. Round-trip: each prize tier -> layout that scores back to it -------

    @Test
    void everyPrizeTierRoundTripsToItsPrize() {
        DemonSealPopulator populator = seeded(7L);
        for (BigDecimal prize : PRIZE_TIERS) {
            Grid grid = populator.populate(GridSize.THREE, prize.doubleValue(), SYMBOL_POOL);
            int points = sealPoints(grid);
            assertThat(expectedPrizeForPoints(points))
                    .as("prize %s -> T=%d", prize, points)
                    .isEqualByComparingTo(prize);
            assertThat(sealTotal(grid)).as("seal count for prize %s", prize).isEqualTo(SEAL_COUNT);
        }
    }

    @Test
    void jackpotPrizesMapToTheHighestPointTiers() {
        DemonSealPopulator populator = seeded(99L);
        for (int i = 0; i < 200; i++) {
            assertThat(sealPoints(populator.populate(GridSize.THREE, 2500.0, SYMBOL_POOL)))
                    .isIn(10, 11);
            assertThat(sealPoints(populator.populate(GridSize.THREE, 25000.0, SYMBOL_POOL)))
                    .isEqualTo(12);
            assertThat(sealPoints(populator.populate(GridSize.THREE, 0.0, SYMBOL_POOL)))
                    .isLessThanOrEqualTo(3);
        }
    }

    // --- 3. False positives: 10,000 losers -> zero winners --------------------

    @Test
    void tenThousandLosersYieldZeroWinners() {
        DemonSealPopulator populator = seeded(424242L);
        int winners = 0;
        for (int i = 0; i < 10_000; i++) {
            Grid grid = populator.populate(GridSize.THREE, 0.0, SYMBOL_POOL);
            if (sealPoints(grid) >= 4) {
                winners++;
            }
            assertThat(sealTotal(grid)).isEqualTo(SEAL_COUNT);
        }
        assertThat(winners).isZero();
    }

    // --- 4. Structural invariants: exactly six seals, filler is non-seal ------

    @ParameterizedTest
    @EnumSource(GridSize.class)
    void everyGridSizeYieldsSixSealsAndNonSealFiller(GridSize size) {
        DemonSealPopulator populator = seeded(11L);
        for (BigDecimal prize : PRIZE_TIERS) {
            Grid grid = populator.populate(size, prize.doubleValue(), SYMBOL_POOL);

            int total = size.dimension() * size.dimension();
            assertThat(grid.getAllCells()).hasSize(total);
            assertThat(sealTotal(grid)).isEqualTo(SEAL_COUNT);

            int filler = 0;
            for (Cell cell : grid.getAllCells()) {
                assertThat(cell.symbol()).isNotEqualTo(GridUtils.EMPTY);
                if (!SEAL_SYMBOLS.contains(cell.symbol())) {
                    assertThat(cell.symbol()).isIn((Object[]) FILLER);
                    filler++;
                }
            }
            assertThat(filler).isEqualTo(total - SEAL_COUNT);
        }
    }

    @Test
    void sealCompositionAlwaysSumsToSixSeals() {
        // gold + silver + broken must always be six seals regardless of T, and the resulting
        // T = 2*gold + silver must map back to the requested prize.
        DemonSealPopulator populator = seeded(33L);
        for (BigDecimal prize : PRIZE_TIERS) {
            Grid grid = populator.populate(GridSize.FOUR, prize.doubleValue(), SYMBOL_POOL);
            int gold = count(grid, GOLD_SEAL);
            int silver = count(grid, SILVER_SEAL);
            int broken = count(grid, BROKEN_SEAL);
            assertThat(gold + silver + broken).isEqualTo(SEAL_COUNT);
            assertThat(expectedPrizeForPoints(2 * gold + silver)).isEqualByComparingTo(prize);
        }
    }

    // --- 5. Aggregate fidelity: a mixed batch realizes its exact total payout -

    @Test
    void mixedBatchRealizesExactTotalPayout() {
        // A constructed pool: exact per-tier counts (the populator must realize each exactly, so the
        // batch's summed payout is preserved — the basis of the "payout ratio is sacred" invariant).
        DemonSealPopulator populator = seeded(2026L);
        BigDecimal expectedTotal = BigDecimal.ZERO;
        BigDecimal actualTotal = BigDecimal.ZERO;
        int[] counts = {50, 20, 10, 5, 3, 2, 1, 1, 1}; // one count per PRIZE_TIERS entry
        for (int tier = 0; tier < PRIZE_TIERS.length; tier++) {
            BigDecimal prize = PRIZE_TIERS[tier];
            for (int n = 0; n < counts[tier]; n++) {
                Grid grid = populator.populate(GridSize.THREE, prize.doubleValue(), SYMBOL_POOL);
                expectedTotal = expectedTotal.add(prize);
                actualTotal = actualTotal.add(expectedPrizeForPoints(sealPoints(grid)));
            }
        }
        assertThat(actualTotal).isEqualByComparingTo(expectedTotal);
    }

    // --- 6. Validation / edge cases -------------------------------------------

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 3.0, 7.0, 50.0, 2499.0, 100000.0})
    void unrealizablePrizeThrows(double prize) {
        assertThatThrownBy(() -> seeded(1L).populate(GridSize.THREE, prize, SYMBOL_POOL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePrizeThrows() {
        assertThatThrownBy(() -> seeded(1L).populate(GridSize.THREE, -5.0, SYMBOL_POOL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullArgumentsThrow() {
        DemonSealPopulator populator = seeded(1L);
        assertThatThrownBy(() -> populator.populate(null, 0.0, SYMBOL_POOL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> populator.populate(GridSize.THREE, 0.0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptySymbolPoolThrows() {
        assertThatThrownBy(() -> seeded(1L).populate(GridSize.THREE, 0.0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void symbolPoolWithoutNonSealFillerThrows() {
        assertThatThrownBy(
                        () ->
                                seeded(1L)
                                        .populate(
                                                GridSize.THREE,
                                                0.0,
                                                List.of(GOLD_SEAL, SILVER_SEAL, BROKEN_SEAL)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allCellsCarryZeroPrizeValueBecausePrizeIsScoredFromSeals() {
        Grid grid = seeded(5L).populate(GridSize.THREE, 100.0, SYMBOL_POOL);
        for (Cell cell : grid.getAllCells()) {
            assertThat(cell.prizeValue()).isZero();
        }
    }

    @Test
    void implementsGridPopulatorInterface() {
        assertThat(seeded(1L)).isInstanceOf(com.luckledger.domain.mechanic.GridPopulator.class);
    }
}
