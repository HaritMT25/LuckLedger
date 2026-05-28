package com.luckledger.mechanic;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WinEvaluatorTest {

    private static Grid uniformGrid(String symbol) {
        int dim = GridSize.THREE.dimension();
        Cell[][] cells = new Cell[dim][dim];
        for (int row = 0; row < dim; row++) {
            for (int col = 0; col < dim; col++) {
                cells[row][col] = new Cell(new Position(row, col), symbol, 0.0);
            }
        }
        return new Grid(GridSize.THREE, cells);
    }

    private static Grid mixedGrid() {
        int dim = GridSize.THREE.dimension();
        Cell[][] cells = new Cell[dim][dim];
        for (int row = 0; row < dim; row++) {
            for (int col = 0; col < dim; col++) {
                String symbol = (row == 0 && col == 0) ? "B" : "A";
                cells[row][col] = new Cell(new Position(row, col), symbol, 0.0);
            }
        }
        return new Grid(GridSize.THREE, cells);
    }

    @Test
    void evaluateReturnsTheResultProducedByTheImplementation() {
        EvaluationResult winner = new EvaluationResult(
                true,
                new BigDecimal("20"),
                List.of(new Position(0, 0), new Position(1, 1), new Position(2, 2)),
                Map.of("A", 3));
        WinEvaluator evaluator = grid -> winner;

        assertThat(evaluator.evaluate(uniformGrid("A"))).isSameAs(winner);
    }

    @Test
    void isAFunctionalInterfaceTakingGridReturningEvaluationResult() {
        // Compiles only if WinEvaluator has exactly one abstract method: Grid -> EvaluationResult.
        WinEvaluator evaluator =
                grid -> new EvaluationResult(false, BigDecimal.ZERO, List.of(), Map.of());

        EvaluationResult result = evaluator.evaluate(uniformGrid("A"));

        assertThat(result.isWinner()).isFalse();
    }

    @Test
    void oneInstanceEvaluatesMultipleGridsForReuseAcrossContexts() {
        // DRY: a single evaluator instance is reused at generation-time verification
        // and at play-time reveal, so it must be stateless across calls.
        WinEvaluator evaluator = grid -> {
            String first = grid.getCell(0, 0).symbol();
            int matches = grid.getCellsBySymbol(first).size();
            boolean win = matches == grid.getAllCells().size();
            return new EvaluationResult(
                    win, win ? new BigDecimal("2") : BigDecimal.ZERO, List.of(), Map.of(first, matches));
        };

        assertThat(evaluator.evaluate(uniformGrid("A")).isWinner()).isTrue();
        assertThat(evaluator.evaluate(mixedGrid()).isWinner()).isFalse();
    }
}
