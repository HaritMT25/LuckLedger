package com.luckledger.domain.mechanic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GridPopulatorTest {

    // Minimal stub honoring the constructive contract: stamps the first symbol
    // and the prize amount onto every cell of a grid of the requested size.
    private static Grid uniformGrid(GridSize size, double prizeAmount, List<String> symbolPool) {
        int n = size.dimension();
        String symbol = symbolPool.get(0);
        Cell[][] cells = new Cell[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                cells[r][c] = new Cell(new Position(r, c), symbol, prizeAmount);
            }
        }
        return new Grid(size, cells);
    }

    @Test
    void shouldBeImplementableAsStrategyAndReturnGridOfRequestedSize() {
        GridPopulator populator = GridPopulatorTest::uniformGrid;

        Grid grid = populator.populate(GridSize.FOUR, 25.0, List.of("STAR", "MOON"));

        assertThat(grid.size()).isEqualTo(GridSize.FOUR);
        assertThat(grid.getAllCells()).hasSize(16);
    }

    @Test
    void shouldReceiveSymbolPoolAndPrizeAmount() {
        GridPopulator populator = GridPopulatorTest::uniformGrid;

        Grid grid = populator.populate(GridSize.THREE, 5.0, List.of("SUN"));

        assertThat(grid.getCellsBySymbol("SUN")).hasSize(9);
        assertThat(grid.getCell(0, 0).prizeValue()).isEqualTo(5.0);
    }
}
