package com.luckledger.domain.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class GridTest {

    private static Cell cell(int row, int col, String symbol) {
        return new Cell(new Position(row, col), symbol, 0.0);
    }

    private static Cell[][] uniformCells(GridSize size, String symbol) {
        int n = size.dimension();
        Cell[][] cells = new Cell[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                cells[r][c] = cell(r, c, symbol);
            }
        }
        return cells;
    }

    @Test
    void shouldExposeSize() {
        Grid grid = new Grid(GridSize.THREE, uniformCells(GridSize.THREE, "STAR"));

        assertThat(grid.size()).isEqualTo(GridSize.THREE);
    }

    @Test
    void shouldReturnCellAtGivenCoordinates() {
        Cell[][] cells = uniformCells(GridSize.THREE, "STAR");
        cells[1][2] = cell(1, 2, "MOON");

        Grid grid = new Grid(GridSize.THREE, cells);

        assertThat(grid.getCell(1, 2).symbol()).isEqualTo("MOON");
        assertThat(grid.getCell(1, 2).position()).isEqualTo(new Position(1, 2));
    }

    @Test
    void shouldReturnAllCellsInRowMajorOrder() {
        Cell[][] cells = new Cell[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                cells[r][c] = cell(r, c, "R" + r + "C" + c);
            }
        }

        Grid grid = new Grid(GridSize.THREE, cells);

        List<Cell> all = grid.getAllCells();
        assertThat(all).hasSize(9);
        assertThat(all).extracting(Cell::symbol)
                .containsExactly("R0C0", "R0C1", "R0C2", "R1C0", "R1C1", "R1C2", "R2C0", "R2C1", "R2C2");
    }

    @Test
    void shouldReturnCellsMatchingSymbol() {
        Cell[][] cells = uniformCells(GridSize.THREE, "STAR");
        cells[0][0] = cell(0, 0, "MOON");
        cells[2][1] = cell(2, 1, "MOON");

        Grid grid = new Grid(GridSize.THREE, cells);

        List<Cell> moons = grid.getCellsBySymbol("MOON");
        assertThat(moons).hasSize(2);
        assertThat(moons).extracting(Cell::position)
                .containsExactly(new Position(0, 0), new Position(2, 1));
    }

    @Test
    void shouldReturnEmptyListWhenNoCellMatchesSymbol() {
        Grid grid = new Grid(GridSize.THREE, uniformCells(GridSize.THREE, "STAR"));

        assertThat(grid.getCellsBySymbol("COMET")).isEmpty();
    }

    @Test
    void shouldBeImmutableToMutationOfSourceArrayAfterConstruction() {
        Cell[][] cells = uniformCells(GridSize.THREE, "STAR");

        Grid grid = new Grid(GridSize.THREE, cells);
        cells[0][0] = cell(0, 0, "TAMPERED");

        assertThat(grid.getCell(0, 0).symbol()).isEqualTo("STAR");
    }

    @Test
    void shouldNotLeakInternalStateThroughGetAllCells() {
        Grid grid = new Grid(GridSize.THREE, uniformCells(GridSize.THREE, "STAR"));

        List<Cell> all = grid.getAllCells();

        assertThatThrownBy(() -> all.add(cell(0, 0, "INJECTED")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectNullSize() {
        assertThatThrownBy(() -> new Grid(null, uniformCells(GridSize.THREE, "STAR")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullCellsArray() {
        assertThatThrownBy(() -> new Grid(GridSize.THREE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectWrongRowCount() {
        Cell[][] cells = new Cell[2][3];
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                cells[r][c] = cell(r, c, "STAR");
            }
        }

        assertThatThrownBy(() -> new Grid(GridSize.THREE, cells))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectWrongColumnCount() {
        Cell[][] cells = new Cell[3][];
        cells[0] = new Cell[] {cell(0, 0, "STAR"), cell(0, 1, "STAR"), cell(0, 2, "STAR")};
        cells[1] = new Cell[] {cell(1, 0, "STAR"), cell(1, 1, "STAR")};
        cells[2] = new Cell[] {cell(2, 0, "STAR"), cell(2, 1, "STAR"), cell(2, 2, "STAR")};

        assertThatThrownBy(() -> new Grid(GridSize.THREE, cells))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullCellWithinArray() {
        Cell[][] cells = uniformCells(GridSize.THREE, "STAR");
        cells[1][1] = null;

        assertThatThrownBy(() -> new Grid(GridSize.THREE, cells))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectGetCellWithOutOfBoundsCoordinates() {
        Grid grid = new Grid(GridSize.THREE, uniformCells(GridSize.THREE, "STAR"));

        assertThatThrownBy(() -> grid.getCell(3, 0))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> grid.getCell(0, -1))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
