package com.luckledger.mechanic;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridPopulator;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameMechanicTest {

    // A minimal, self-consistent mechanic: the populator stamps the first pool symbol on every
    // cell (a winner) when prizeAmount > 0, and the evaluator recognises that uniform pattern and
    // reports the prize it finds on the grid. Populator and evaluator are bundled deliberately so
    // the round-trip below proves a mechanic's two halves agree.
    private static final class UniformMatchMechanic implements GameMechanic {

        @Override
        public MechanicType getType() {
            return MechanicType.MATCH_3;
        }

        @Override
        public GridPopulator createPopulator() {
            return (size, prizeAmount, symbolPool) -> {
                int n = size.dimension();
                String symbol = symbolPool.get(0);
                Cell[][] cells = new Cell[n][n];
                for (int r = 0; r < n; r++) {
                    for (int c = 0; c < n; c++) {
                        cells[r][c] = new Cell(new Position(r, c), symbol, prizeAmount);
                    }
                }
                return new Grid(size, cells);
            };
        }

        @Override
        public WinEvaluator createEvaluator() {
            return grid -> {
                String first = grid.getCell(0, 0).symbol();
                List<Cell> matches = grid.getCellsBySymbol(first);
                boolean win = matches.size() == grid.getAllCells().size();
                List<Position> positions = new ArrayList<>();
                for (Cell cell : matches) {
                    positions.add(cell.position());
                }
                BigDecimal prize =
                        win ? BigDecimal.valueOf(grid.getCell(0, 0).prizeValue()) : BigDecimal.ZERO;
                return new EvaluationResult(win, prize, positions, Map.of(first, matches.size()));
            };
        }

        @Override
        public List<String> getDefaultSymbolPool() {
            return List.of("CHERRY", "BELL", "STAR", "SEVEN");
        }
    }

    // A second, structurally different mechanic. It exists only to prove the interface is an
    // Open/Closed extension point: a new mechanic plugs in by implementing GameMechanic, with no
    // change to existing code, and is usable through the GameMechanic type.
    private static final class AlternateMechanic implements GameMechanic {

        @Override
        public MechanicType getType() {
            return MechanicType.NUMBER_MATCH;
        }

        @Override
        public GridPopulator createPopulator() {
            return (size, prizeAmount, symbolPool) -> {
                int n = size.dimension();
                Cell[][] cells = new Cell[n][n];
                for (int r = 0; r < n; r++) {
                    for (int c = 0; c < n; c++) {
                        cells[r][c] = new Cell(new Position(r, c), symbolPool.get(0), 0.0);
                    }
                }
                return new Grid(size, cells);
            };
        }

        @Override
        public WinEvaluator createEvaluator() {
            return grid -> new EvaluationResult(false, BigDecimal.ZERO, List.of(), Map.of());
        }

        @Override
        public List<String> getDefaultSymbolPool() {
            return List.of("1", "2", "3", "4", "5");
        }
    }

    @Test
    void getTypeReturnsTheDeclaredMechanicType() {
        GameMechanic mechanic = new UniformMatchMechanic();

        assertThat(mechanic.getType()).isEqualTo(MechanicType.MATCH_3);
    }

    @Test
    void createPopulatorReturnsAUsablePopulator() {
        GameMechanic mechanic = new UniformMatchMechanic();

        GridPopulator populator = mechanic.createPopulator();
        Grid grid = populator.populate(GridSize.THREE, 20.0, mechanic.getDefaultSymbolPool());

        assertThat(populator).isNotNull();
        assertThat(grid.size()).isEqualTo(GridSize.THREE);
        assertThat(grid.getAllCells()).hasSize(9);
    }

    @Test
    void createEvaluatorReturnsAUsableEvaluator() {
        GameMechanic mechanic = new UniformMatchMechanic();

        WinEvaluator evaluator = mechanic.createEvaluator();
        Grid grid =
                mechanic.createPopulator().populate(GridSize.THREE, 0.0, mechanic.getDefaultSymbolPool());

        assertThat(evaluator).isNotNull();
        assertThat(evaluator.evaluate(grid)).isNotNull();
    }

    @Test
    void getDefaultSymbolPoolIsNonEmpty() {
        GameMechanic mechanic = new UniformMatchMechanic();

        assertThat(mechanic.getDefaultSymbolPool()).isNotEmpty();
    }

    @Test
    void populatorAndEvaluatorFromTheSameMechanicRoundTrip() {
        // The factory's purpose: the populator builds a layout encoding a prize, and the evaluator
        // from the same mechanic reads back exactly that prize. The two halves must agree.
        GameMechanic mechanic = new UniformMatchMechanic();
        BigDecimal targetPrize = new BigDecimal("740");

        Grid winner =
                mechanic
                        .createPopulator()
                        .populate(GridSize.THREE, targetPrize.doubleValue(), mechanic.getDefaultSymbolPool());
        EvaluationResult result = mechanic.createEvaluator().evaluate(winner);

        assertThat(result.isWinner()).isTrue();
        assertThat(result.prizeAmount()).isEqualByComparingTo(targetPrize);
    }

    @Test
    void servesAsAnOpenClosedExtensionPointForNewMechanics() {
        // Two distinct implementations are used purely through the GameMechanic type. Adding the
        // second required implementing the interface only — no existing mechanic was touched.
        List<GameMechanic> registry = List.of(new UniformMatchMechanic(), new AlternateMechanic());

        assertThat(registry)
                .extracting(GameMechanic::getType)
                .containsExactly(MechanicType.MATCH_3, MechanicType.NUMBER_MATCH);
        for (GameMechanic mechanic : registry) {
            assertThat(mechanic.createPopulator()).isNotNull();
            assertThat(mechanic.createEvaluator()).isNotNull();
            assertThat(mechanic.getDefaultSymbolPool()).isNotEmpty();
        }
    }
}
