package com.luckledger.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GridUtilsTest {

    private static final long SEED = 42L;

    private static GridUtils seeded() {
        return new GridUtils(new Random(SEED));
    }

    /** Builds a grid filled entirely with {@link GridUtils#EMPTY} cells. */
    private static Grid emptyGrid(GridSize size) {
        int dim = size.dimension();
        Cell[][] cells = new Cell[dim][dim];
        for (int row = 0; row < dim; row++) {
            for (int col = 0; col < dim; col++) {
                cells[row][col] = new Cell(new Position(row, col), GridUtils.EMPTY, 0.0);
            }
        }
        return new Grid(size, cells);
    }

    /** Builds a 3x3 grid from a symbol matrix; prize values default to 0. */
    private static Grid gridOf(String[][] symbols) {
        int dim = GridSize.THREE.dimension();
        Cell[][] cells = new Cell[dim][dim];
        for (int row = 0; row < dim; row++) {
            for (int col = 0; col < dim; col++) {
                cells[row][col] = new Cell(new Position(row, col), symbols[row][col], 0.0);
            }
        }
        return new Grid(GridSize.THREE, cells);
    }

    // ----- construction -----

    @Test
    void constructorRejectsNullRandom() {
        assertThatThrownBy(() -> new GridUtils(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void noArgConstructorIsUsableForProduction() {
        // Production default uses SecureRandom; the instance must still fill grids.
        GridUtils utils = new GridUtils();
        Grid filled = utils.fillRemaining(emptyGrid(GridSize.THREE), List.of("A"), Set.of());

        assertThat(filled.getCellsBySymbol("A")).hasSize(9);
    }

    // ----- fillRemaining -----

    @Test
    void fillRemainingReplacesOnlyEmptyCells() {
        String[][] symbols = {
            {"A", GridUtils.EMPTY, "A"},
            {GridUtils.EMPTY, "A", GridUtils.EMPTY},
            {"A", GridUtils.EMPTY, "A"}
        };
        Grid filled = seeded().fillRemaining(gridOf(symbols), List.of("B"), Set.of());

        assertThat(filled.getCellsBySymbol(GridUtils.EMPTY)).isEmpty();
        assertThat(filled.getCellsBySymbol("A")).hasSize(5);
        assertThat(filled.getCellsBySymbol("B")).hasSize(4);
    }

    @Test
    void fillRemainingNeverUsesExcludedSymbols() {
        Grid filled =
                seeded()
                        .fillRemaining(
                                emptyGrid(GridSize.FIVE), List.of("A", "B", "C"), Set.of("A", "B"));

        assertThat(filled.getCellsBySymbol("A")).isEmpty();
        assertThat(filled.getCellsBySymbol("B")).isEmpty();
        assertThat(filled.getCellsBySymbol("C")).hasSize(25);
    }

    @Test
    void fillRemainingIsDeterministicWithSeededRandom() {
        List<String> pool = List.of("A", "B", "C", "D");

        Grid first = seeded().fillRemaining(emptyGrid(GridSize.FOUR), pool, Set.of());
        Grid second = seeded().fillRemaining(emptyGrid(GridSize.FOUR), pool, Set.of());

        List<String> firstSymbols = first.getAllCells().stream().map(Cell::symbol).toList();
        List<String> secondSymbols = second.getAllCells().stream().map(Cell::symbol).toList();
        assertThat(firstSymbols).isEqualTo(secondSymbols);
    }

    @Test
    void fillRemainingThrowsWhenNoCandidatesRemainButEmptiesExist() {
        assertThatThrownBy(
                        () ->
                                seeded()
                                        .fillRemaining(
                                                emptyGrid(GridSize.THREE), List.of("A"), Set.of("A")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fillRemainingWithoutEmptyCellsReturnsEquivalentGrid() {
        String[][] symbols = {
            {"A", "B", "C"},
            {"D", "E", "F"},
            {"G", "H", "I"}
        };
        Grid filled = seeded().fillRemaining(gridOf(symbols), List.of("Z"), Set.of());

        assertThat(filled.getCellsBySymbol("Z")).isEmpty();
        assertThat(filled.getAllCells().stream().map(Cell::symbol).toList())
                .containsExactly("A", "B", "C", "D", "E", "F", "G", "H", "I");
    }

    @Test
    void fillRemainingPreservesPrizeValuesOfFilledCells() {
        int dim = GridSize.THREE.dimension();
        Cell[][] cells = new Cell[dim][dim];
        for (int row = 0; row < dim; row++) {
            for (int col = 0; col < dim; col++) {
                cells[row][col] = new Cell(new Position(row, col), GridUtils.EMPTY, 0.0);
            }
        }
        cells[1][1] = new Cell(new Position(1, 1), GridUtils.EMPTY, 25.0);
        Grid grid = new Grid(GridSize.THREE, cells);

        Grid filled = seeded().fillRemaining(grid, List.of("A"), Set.of());

        Cell center = filled.getCell(1, 1);
        assertThat(center.symbol()).isEqualTo("A");
        assertThat(center.prizeValue()).isEqualTo(25.0);
    }

    @Test
    void fillRemainingNeverSelectsTheEmptySentinelEvenIfPresentInPool() {
        Grid filled =
                seeded()
                        .fillRemaining(
                                emptyGrid(GridSize.THREE), List.of("A", GridUtils.EMPTY), Set.of());

        assertThat(filled.getCellsBySymbol(GridUtils.EMPTY)).isEmpty();
        assertThat(filled.getCellsBySymbol("A")).hasSize(9);
    }

    @Test
    void fillRemainingDistributesAcrossAllowedSymbols() {
        // Distribution sanity: over many single-cell fills each allowed symbol is used,
        // roughly uniformly, and never an excluded one. Seeded, so this never flakes.
        GridUtils utils = seeded();
        Map<String, Integer> counts = new HashMap<>();
        int trials = 3000;
        for (int i = 0; i < trials; i++) {
            Grid filled =
                    utils.fillRemaining(emptyGrid(GridSize.THREE), List.of("A", "B", "C"), Set.of());
            for (Cell cell : filled.getAllCells()) {
                counts.merge(cell.symbol(), 1, Integer::sum);
            }
        }

        assertThat(counts.keySet()).containsExactlyInAnyOrder("A", "B", "C");
        int totalCells = trials * 9;
        int expected = totalCells / 3;
        assertThat(counts.values()).allSatisfy(c -> assertThat(c).isBetween(expected * 4 / 5, expected * 6 / 5));
    }

    // ----- getRandomSymbols -----

    @Test
    void getRandomSymbolsReturnsRequestedCountOfDistinctNonExcludedSymbols() {
        List<String> pool = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
        List<String> picked = seeded().getRandomSymbols(pool, 4, Set.of("1", "2"));

        assertThat(picked).hasSize(4).doesNotHaveDuplicates().doesNotContain("1", "2");
        assertThat(pool).containsAll(picked);
    }

    @Test
    void getRandomSymbolsThrowsWhenCountExceedsAvailable() {
        assertThatThrownBy(() -> seeded().getRandomSymbols(List.of("A", "B"), 3, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getRandomSymbolsThrowsWhenExclusionsLeaveTooFew() {
        assertThatThrownBy(
                        () ->
                                seeded()
                                        .getRandomSymbols(
                                                List.of("A", "B", "C"), 2, Set.of("A", "B")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getRandomSymbolsZeroCountReturnsEmptyList() {
        assertThat(seeded().getRandomSymbols(List.of("A", "B"), 0, Set.of())).isEmpty();
    }

    @Test
    void getRandomSymbolsNegativeCountThrows() {
        assertThatThrownBy(() -> seeded().getRandomSymbols(List.of("A"), -1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getRandomSymbolsDeduplicatesPool() {
        List<String> picked = seeded().getRandomSymbols(List.of("A", "A", "B"), 2, Set.of());

        assertThat(picked).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void getRandomSymbolsIsDeterministicWithSeededRandom() {
        List<String> pool = List.of("A", "B", "C", "D", "E", "F");

        List<String> first = seeded().getRandomSymbols(pool, 3, Set.of());
        List<String> second = seeded().getRandomSymbols(pool, 3, Set.of());

        assertThat(first).isEqualTo(second);
    }

    // ----- placeSymbolsAtPositions -----

    @Test
    void placeSymbolsAtPositionsPlacesSymbolAtGivenPositionsOnly() {
        Grid placed =
                seeded()
                        .placeSymbolsAtPositions(
                                emptyGrid(GridSize.THREE),
                                "WIN",
                                List.of(new Position(0, 0), new Position(2, 2)));

        assertThat(placed.getCell(0, 0).symbol()).isEqualTo("WIN");
        assertThat(placed.getCell(2, 2).symbol()).isEqualTo("WIN");
        assertThat(placed.getCellsBySymbol("WIN")).hasSize(2);
        assertThat(placed.getCell(1, 1).symbol()).isEqualTo(GridUtils.EMPTY);
    }

    @Test
    void placeSymbolsAtPositionsPreservesExistingPrizeValue() {
        int dim = GridSize.THREE.dimension();
        Cell[][] cells = new Cell[dim][dim];
        for (int row = 0; row < dim; row++) {
            for (int col = 0; col < dim; col++) {
                cells[row][col] = new Cell(new Position(row, col), GridUtils.EMPTY, 0.0);
            }
        }
        cells[0][0] = new Cell(new Position(0, 0), GridUtils.EMPTY, 740.0);
        Grid grid = new Grid(GridSize.THREE, cells);

        Grid placed = seeded().placeSymbolsAtPositions(grid, "STAR", List.of(new Position(0, 0)));

        assertThat(placed.getCell(0, 0).symbol()).isEqualTo("STAR");
        assertThat(placed.getCell(0, 0).prizeValue()).isEqualTo(740.0);
    }

    @Test
    void placeSymbolsAtPositionsWithEmptyPositionsReturnsEquivalentGrid() {
        Grid grid = emptyGrid(GridSize.THREE);
        Grid placed = seeded().placeSymbolsAtPositions(grid, "WIN", List.of());

        assertThat(placed.getCellsBySymbol(GridUtils.EMPTY)).hasSize(9);
    }

    @Test
    void placeSymbolsAtPositionsRejectsBlankSymbol() {
        assertThatThrownBy(
                        () ->
                                seeded()
                                        .placeSymbolsAtPositions(
                                                emptyGrid(GridSize.THREE),
                                                " ",
                                                List.of(new Position(0, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void placeSymbolsAtPositionsRejectsOutOfBoundsPosition() {
        assertThatThrownBy(
                        () ->
                                seeded()
                                        .placeSymbolsAtPositions(
                                                emptyGrid(GridSize.THREE),
                                                "WIN",
                                                List.of(new Position(3, 0))))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    // ----- hasAccidentalWin -----

    @Test
    void hasAccidentalWinReturnsTrueWhenEvaluatorReportsWinner() {
        WinEvaluator winner =
                grid -> new EvaluationResult(true, new BigDecimal("20"), List.of(), Map.of());

        assertThat(seeded().hasAccidentalWin(emptyGrid(GridSize.THREE), winner)).isTrue();
    }

    @Test
    void hasAccidentalWinReturnsFalseWhenEvaluatorReportsLoser() {
        WinEvaluator loser =
                grid -> new EvaluationResult(false, BigDecimal.ZERO, List.of(), Map.of());

        assertThat(seeded().hasAccidentalWin(emptyGrid(GridSize.THREE), loser)).isFalse();
    }

    @Test
    void constructiveLoserFillKeepsWinningSymbolBelowMatchThreshold() {
        // Mirrors the Match-3 constructive loser path: place 2 of the winning symbol,
        // fill the rest excluding it, and confirm the winning symbol never reaches 3.
        WinEvaluator matchThree =
                grid -> {
                    Map<String, Integer> counts = new HashMap<>();
                    for (Cell cell : grid.getAllCells()) {
                        counts.merge(cell.symbol(), 1, Integer::sum);
                    }
                    int maxWin = counts.getOrDefault("WIN", 0);
                    boolean win = maxWin >= 3;
                    return new EvaluationResult(
                            win, win ? new BigDecimal("20") : BigDecimal.ZERO, List.of(), counts);
                };

        GridUtils utils = seeded();
        Grid placed =
                utils.placeSymbolsAtPositions(
                        emptyGrid(GridSize.THREE),
                        "WIN",
                        List.of(new Position(0, 0), new Position(0, 1)));
        Grid filled = utils.fillRemaining(placed, List.of("A", "B", "C", "D"), Set.of("WIN"));

        assertThat(filled.getCellsBySymbol("WIN")).hasSize(2);
        assertThat(utils.hasAccidentalWin(filled, matchThree)).isFalse();
    }
}
