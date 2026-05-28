package com.luckledger.domain.generation.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.Position;
import org.junit.jupiter.api.Test;

class ThemedGridTest {

    private static ThemedCell cell(int row, int col) {
        return new ThemedCell(
                new Position(row, col), new ThemedSymbol("SYM_" + row + col, "🤠", null, "Hat"));
    }

    private static ThemedCell[][] grid(int dim) {
        ThemedCell[][] cells = new ThemedCell[dim][dim];
        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                cells[r][c] = cell(r, c);
            }
        }
        return cells;
    }

    @Test
    void exposesSizeAndCells() {
        int dim = GridSize.THREE.dimension();
        ThemedGrid themed = new ThemedGrid(GridSize.THREE, grid(dim));

        assertThat(themed.size()).isEqualTo(GridSize.THREE);
        assertThat(themed.getAllCells()).hasSize(dim * dim);
        assertThat(themed.getCell(1, 2).themedSymbol().abstractSymbol()).isEqualTo("SYM_12");
    }

    @Test
    void backingArrayIsDefensivelyCopied() {
        int dim = GridSize.THREE.dimension();
        ThemedCell[][] source = grid(dim);
        ThemedGrid themed = new ThemedGrid(GridSize.THREE, source);

        source[0][0] = cell(2, 2); // mutating the source must not affect the grid
        assertThat(themed.getCell(0, 0).themedSymbol().abstractSymbol()).isEqualTo("SYM_00");
    }

    @Test
    void getAllCellsIsUnmodifiable() {
        ThemedGrid themed = new ThemedGrid(GridSize.THREE, grid(GridSize.THREE.dimension()));

        assertThatThrownBy(() -> themed.getAllCells().add(cell(0, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void wrongRowCountIsRejected() {
        assertThatThrownBy(() -> new ThemedGrid(GridSize.THREE, grid(GridSize.FOUR.dimension())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outOfBoundsAccessIsRejected() {
        ThemedGrid themed = new ThemedGrid(GridSize.THREE, grid(GridSize.THREE.dimension()));

        assertThatThrownBy(() -> themed.getCell(3, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> themed.getCell(0, -1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThatThrownBy(() -> new ThemedGrid(null, grid(3)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ThemedGrid(GridSize.THREE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
