package com.luckledger.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridPopulator;
import com.luckledger.domain.mechanic.MechanicType;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CelestialFortuneMechanicTest {

    private final CelestialFortuneMechanic mechanic = new CelestialFortuneMechanic();

    @Test
    void getTypeIsCelestialFortune() {
        assertThat(mechanic.getType()).isEqualTo(MechanicType.CELESTIAL_FORTUNE);
    }

    @Test
    void createPopulatorReturnsACelestialFortunePopulator() {
        GridPopulator populator = mechanic.createPopulator();

        assertThat(populator).isInstanceOf(CelestialFortunePopulator.class);
    }

    @Test
    void createEvaluatorReturnsACelestialFortuneEvaluator() {
        WinEvaluator evaluator = mechanic.createEvaluator();

        assertThat(evaluator).isInstanceOf(CelestialFortuneEvaluator.class);
    }

    @Test
    void createReturnsFreshInstancesEachCall() {
        // "create" semantics: each populator owns an independent randomness source, so a caller can
        // build many tickets concurrently without sharing RNG state.
        assertThat(mechanic.createPopulator()).isNotSameAs(mechanic.createPopulator());
        assertThat(mechanic.createEvaluator()).isNotSameAs(mechanic.createEvaluator());
    }

    @Test
    void defaultSymbolPoolHasExactlyThePoolSizeDistinctNumbers() {
        List<String> pool = mechanic.getDefaultSymbolPool();

        assertThat(pool).hasSize(CelestialFortuneEvaluator.NUMBER_POOL_SIZE);
        assertThat(new HashSet<>(pool)).hasSize(CelestialFortuneEvaluator.NUMBER_POOL_SIZE);
    }

    @Test
    void defaultSymbolPoolIsImmutable() {
        List<String> pool = mechanic.getDefaultSymbolPool();

        assertThatThrownBy(() -> pool.add("99")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defaultSymbolPoolSatisfiesItsOwnPopulator() {
        // The pool the factory advertises must meet the populator's minimum-distinct requirement.
        Grid grid =
                mechanic
                        .createPopulator()
                        .populate(
                                CelestialFortuneEvaluator.GRID_SIZE, 0.0, mechanic.getDefaultSymbolPool());

        assertThat(grid.size()).isEqualTo(CelestialFortuneEvaluator.GRID_SIZE);
    }

    @Test
    void bundledPopulatorAndEvaluatorRoundTripEveryPrizeTier() {
        // The factory's contract: the populator it bundles builds a layout encoding a prize, and the
        // evaluator it bundles reads back exactly that prize — for every calibrated tier.
        GridPopulator populator = mechanic.createPopulator();
        WinEvaluator evaluator = mechanic.createEvaluator();
        List<String> pool = mechanic.getDefaultSymbolPool();

        for (BigDecimal prize :
                List.of(
                        new BigDecimal("0"),
                        new BigDecimal("2"),
                        new BigDecimal("20"),
                        new BigDecimal("740"))) {
            for (int i = 0; i < 200; i++) {
                Grid grid =
                        populator.populate(CelestialFortuneEvaluator.GRID_SIZE, prize.doubleValue(), pool);
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
            Grid loser = populator.populate(CelestialFortuneEvaluator.GRID_SIZE, 0.0, pool);

            assertThat(evaluator.evaluate(loser).isWinner()).isFalse();
        }
    }
}
