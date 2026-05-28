package com.luckledger.domain.mechanic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Grid {

    private final GridSize size;
    private final Cell[][] cells;

    public Grid(GridSize size, Cell[][] cells) {
        Objects.requireNonNull(size, "size must not be null");
        Objects.requireNonNull(cells, "cells must not be null");
        int dimension = size.dimension();
        if (cells.length != dimension) {
            throw new IllegalArgumentException(
                    "cells must have " + dimension + " rows, was " + cells.length);
        }
        Cell[][] copy = new Cell[dimension][dimension];
        for (int row = 0; row < dimension; row++) {
            Cell[] sourceRow = cells[row];
            Objects.requireNonNull(sourceRow, "cell row " + row + " must not be null");
            if (sourceRow.length != dimension) {
                throw new IllegalArgumentException(
                        "cell row " + row + " must have " + dimension + " columns, was " + sourceRow.length);
            }
            for (int col = 0; col < dimension; col++) {
                copy[row][col] = Objects.requireNonNull(
                        sourceRow[col], "cell at (" + row + ", " + col + ") must not be null");
            }
        }
        this.size = size;
        this.cells = copy;
    }

    public GridSize size() {
        return size;
    }

    public Cell getCell(int row, int col) {
        int dimension = size.dimension();
        if (row < 0 || row >= dimension || col < 0 || col >= dimension) {
            throw new IndexOutOfBoundsException(
                    "coordinates (" + row + ", " + col + ") out of bounds for grid of size " + dimension);
        }
        return cells[row][col];
    }

    public List<Cell> getAllCells() {
        List<Cell> all = new ArrayList<>(size.dimension() * size.dimension());
        for (Cell[] row : cells) {
            Collections.addAll(all, row);
        }
        return Collections.unmodifiableList(all);
    }

    public List<Cell> getCellsBySymbol(String symbol) {
        List<Cell> matches = new ArrayList<>();
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                if (cell.symbol().equals(symbol)) {
                    matches.add(cell);
                }
            }
        }
        return Collections.unmodifiableList(matches);
    }
}
