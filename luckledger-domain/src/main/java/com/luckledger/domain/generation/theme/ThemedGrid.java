package com.luckledger.domain.generation.theme;

import com.luckledger.domain.mechanic.GridSize;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The themed counterpart of a mechanic {@code Grid}: a square arrangement of {@link ThemedCell}s
 * sized by a {@link GridSize}. Theming maps each abstract cell to its visual without changing the
 * grid's shape or the underlying outcome — this is Layer 4 presentation only.
 *
 * <p>The backing array is defensively copied on construction and never exposed directly, so the grid
 * is effectively immutable.
 */
public final class ThemedGrid {

    private final GridSize size;
    private final ThemedCell[][] cells;

    public ThemedGrid(GridSize size, ThemedCell[][] cells) {
        Objects.requireNonNull(size, "size must not be null");
        Objects.requireNonNull(cells, "cells must not be null");
        int dimension = size.dimension();
        if (cells.length != dimension) {
            throw new IllegalArgumentException(
                    "cells must have " + dimension + " rows, was " + cells.length);
        }
        ThemedCell[][] copy = new ThemedCell[dimension][dimension];
        for (int row = 0; row < dimension; row++) {
            ThemedCell[] sourceRow = cells[row];
            Objects.requireNonNull(sourceRow, "cell row " + row + " must not be null");
            if (sourceRow.length != dimension) {
                throw new IllegalArgumentException(
                        "cell row " + row + " must have " + dimension + " columns, was " + sourceRow.length);
            }
            for (int col = 0; col < dimension; col++) {
                copy[row][col] =
                        Objects.requireNonNull(
                                sourceRow[col], "cell at (" + row + ", " + col + ") must not be null");
            }
        }
        this.size = size;
        this.cells = copy;
    }

    public GridSize size() {
        return size;
    }

    public ThemedCell getCell(int row, int col) {
        int dimension = size.dimension();
        if (row < 0 || row >= dimension || col < 0 || col >= dimension) {
            throw new IndexOutOfBoundsException(
                    "coordinates (" + row + ", " + col + ") out of bounds for grid of size " + dimension);
        }
        return cells[row][col];
    }

    public List<ThemedCell> getAllCells() {
        List<ThemedCell> all = new ArrayList<>(size.dimension() * size.dimension());
        for (ThemedCell[] row : cells) {
            Collections.addAll(all, row);
        }
        return Collections.unmodifiableList(all);
    }
}
