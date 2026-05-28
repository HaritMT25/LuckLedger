package com.luckledger.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridPopulator;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.MechanicType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemonSealMechanicTest {

    /** A 3x3 grid (9 cells) comfortably holds the six seals plus filler. */
    private static final GridSize GRID_SIZE = GridSize.THREE;

    /** Every distinct prize on the calibrated Demon Seal ladder. */
    private static final List<BigDecimal> PRIZE_TIERS =
            List.of(
                    new BigDecimal("0"),
                    new BigDecimal("2"),
                    new BigDecimal("4"),
                    new BigDecimal("10"),
                    new BigDecimal("25"),
                    new BigDecimal("100"),
                    new BigDecimal("300"),
                    new BigDecimal("2500"),
                    new BigDecimal("25000"));

    private final DemonSealMechanic mechanic = new DemonSealMechanic();

    @Test
    void getTypeIsDemonSeal() {
        assertThat(mechanic.getType()).isEqualTo(MechanicType.DEMON_SEAL);
    }

    @Test
    void createPopulatorReturnsADemonSealPopulator() {
        assertThat(mechanic.createPopulator()).isInstanceOf(DemonSealPopulator.class);
    }

    @Test
    void createEvaluatorReturnsADemonSealEvaluator() {
        assertThat(mechanic.createEvaluator()).isInstanceOf(DemonSealEvaluator.class);
    }

    @Test
    void createReturnsFreshInstancesEachCall() {
        // "create" semantics: each populator owns an independent randomness source, so a caller can
        // build many tickets concurrently without sharing RNG state.
        assertThat(mechanic.createPopulator()).isNotSameAs(mechanic.createPopulator());
        assertThat(mechanic.createEvaluator()).isNotSameAs(mechanic.createEvaluator());
    }

    @Test
    void defaultSymbolPoolCarriesTheSealsAndAtLeastOneNonSealFiller() {
        List<String> pool = mechanic.getDefaultSymbolPool();

        assertThat(pool)
                .contains(
                        DemonSealEvaluator.GOLD_SEAL,
                        DemonSealEvaluator.SILVER_SEAL,
                        DemonSealEvaluator.BROKEN_SEAL);
        assertThat(pool)
                .anyMatch(
                        s ->
                                !s.equals(DemonSealEvaluator.GOLD_SEAL)
                                        && !s.equals(DemonSealEvaluator.SILVER_SEAL)
                                        && !s.equals(DemonSealEvaluator.BROKEN_SEAL));
    }

    @Test
    void defaultSymbolPoolIsImmutable() {
        List<String> pool = mechanic.getDefaultSymbolPool();

        assertThatThrownBy(() -> pool.add("EXTRA")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defaultSymbolPoolSatisfiesItsOwnPopulator() {
        Grid grid = mechanic.createPopulator().populate(GRID_SIZE, 0.0, mechanic.getDefaultSymbolPool());

        assertThat(grid.size()).isEqualTo(GRID_SIZE);
    }

    @Test
    void bundledPopulatorAndEvaluatorRoundTripEveryPrizeTier() {
        // The factory's contract: the populator it bundles builds a layout encoding a prize, and the
        // evaluator it bundles reads back exactly that prize — for every calibrated tier.
        GridPopulator populator = mechanic.createPopulator();
        WinEvaluator evaluator = mechanic.createEvaluator();
        List<String> pool = mechanic.getDefaultSymbolPool();

        for (BigDecimal prize : PRIZE_TIERS) {
            for (int i = 0; i < 200; i++) {
                Grid grid = populator.populate(GRID_SIZE, prize.doubleValue(), pool);
                EvaluationResult result = evaluator.evaluate(grid);

                assertThat(result.prizeAmount()).isEqualByComparingTo(prize);
                assertThat(result.isWinner()).isEqualTo(prize.signum() > 0);
            }
        }
    }

    @Test
    void losersFromTheBundledPairNeverEvaluateAsWinners() {
        // Required false-positive guard: 10,000 constructively-built $0 tickets, zero accidental wins.
        GridPopulator populator = mechanic.createPopulator();
        WinEvaluator evaluator = mechanic.createEvaluator();
        List<String> pool = mechanic.getDefaultSymbolPool();

        for (int i = 0; i < 10_000; i++) {
            Grid loser = populator.populate(GRID_SIZE, 0.0, pool);

            assertThat(evaluator.evaluate(loser).isWinner()).isFalse();
        }
    }
}
