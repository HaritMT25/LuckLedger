package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.Position;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Shared, constructive helpers for {@code GridPopulator} implementations.
 *
 * <p>Used via composition — populators hold a {@code GridUtils} instance rather than extending a
 * base class (no inheritance hierarchy for populators). Every method runs in a single pass with no
 * reject-and-retry, preserving the constructive-generation invariant.
 *
 * <p>A {@link Grid} is immutable and every {@link Cell} carries a non-blank symbol, so there is no
 * native "empty" cell. Populators mark a cell as not-yet-filled with the {@link #EMPTY} sentinel;
 * {@link #fillRemaining} replaces exactly those cells.
 *
 * <p>Randomness is constructor-injected: production wires a {@link SecureRandom}; tests pass a
 * seeded {@link java.util.Random} for deterministic, reproducible layouts.
 */
public final class GridUtils {

    /** Sentinel symbol marking a cell that has not yet been filled with a real symbol. */
    public static final String EMPTY = "_EMPTY_";

    private final RandomGenerator random;

    /** Creates an instance backed by a {@link SecureRandom} for production use. */
    public GridUtils() {
        this(new SecureRandom());
    }

    /**
     * Creates an instance backed by the given randomness source.
     *
     * @param random the randomness source; never {@code null}
     */
    public GridUtils(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Returns a copy of {@code grid} with every {@link #EMPTY} cell replaced by a random symbol
     * drawn (with replacement) from {@code symbolPool}, skipping anything in {@code excludeSymbols}.
     *
     * <p>Excluding the winning symbol prevents the fill from manufacturing an accidental win;
     * callers still verify with {@link #hasAccidentalWin} as a safety check. Non-empty cells and
     * every cell's prize value are preserved unchanged.
     *
     * @param grid           the grid to fill; never {@code null}
     * @param symbolPool     candidate symbols; never {@code null}
     * @param excludeSymbols symbols never to place; never {@code null}
     * @return a new grid with no remaining {@link #EMPTY} cells
     * @throws IllegalArgumentException if empty cells remain but no candidate symbols survive the
     *                                  exclusions
     */
    public Grid fillRemaining(Grid grid, List<String> symbolPool, Set<String> excludeSymbols) {
        Objects.requireNonNull(grid, "grid must not be null");
        Objects.requireNonNull(symbolPool, "symbolPool must not be null");
        Objects.requireNonNull(excludeSymbols, "excludeSymbols must not be null");

        List<String> candidates = candidates(symbolPool, excludeSymbols);
        int dim = grid.size().dimension();
        Cell[][] cells = copyCells(grid);
        boolean hasEmpty = false;
        for (int row = 0; row < dim; row++) {
            for (int col = 0; col < dim; col++) {
                if (EMPTY.equals(cells[row][col].symbol())) {
                    hasEmpty = true;
                }
            }
        }
        if (hasEmpty && candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "cannot fill empty cells: no candidate symbols remain after exclusions");
        }
        for (int row = 0; row < dim; row++) {
            for (int col = 0; col < dim; col++) {
                Cell cell = cells[row][col];
                if (EMPTY.equals(cell.symbol())) {
                    String pick = candidates.get(random.nextInt(candidates.size()));
                    cells[row][col] = new Cell(cell.position(), pick, cell.prizeValue());
                }
            }
        }
        return new Grid(grid.size(), cells);
    }

    /**
     * Selects {@code count} distinct symbols from {@code pool}, never drawing from {@code exclude}.
     *
     * <p>Duplicate pool entries collapse to one candidate. Selection is a partial Fisher-Yates
     * shuffle, so the result is uniform and reproducible under a seeded randomness source.
     *
     * @param pool    candidate symbols; never {@code null}
     * @param count   how many distinct symbols to return; must be {@code >= 0}
     * @param exclude symbols never to draw; never {@code null}
     * @return {@code count} distinct symbols, in random order
     * @throws IllegalArgumentException if {@code count} is negative or exceeds the number of
     *                                  distinct, non-excluded symbols available
     */
    public List<String> getRandomSymbols(List<String> pool, int count, Set<String> exclude) {
        Objects.requireNonNull(pool, "pool must not be null");
        Objects.requireNonNull(exclude, "exclude must not be null");
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative, was " + count);
        }
        List<String> candidates = candidates(pool, exclude);
        if (count > candidates.size()) {
            throw new IllegalArgumentException(
                    "cannot select " + count + " distinct symbols; only " + candidates.size()
                            + " available after exclusions");
        }
        for (int i = 0; i < count; i++) {
            int j = i + random.nextInt(candidates.size() - i);
            String swap = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, swap);
        }
        return new ArrayList<>(candidates.subList(0, count));
    }

    /**
     * Returns a copy of {@code grid} with {@code symbol} placed at each of {@code positions}.
     *
     * <p>Only the symbol changes; each affected cell keeps its prize value, and all other cells are
     * untouched. Used by populators to lay down a winning pattern before filling the remainder.
     *
     * @param grid      the grid to copy; never {@code null}
     * @param symbol    the symbol to place; never {@code null} or blank
     * @param positions the positions to overwrite; never {@code null}
     * @return a new grid with the symbol placed
     * @throws IndexOutOfBoundsException if any position lies outside the grid
     */
    public Grid placeSymbolsAtPositions(Grid grid, String symbol, List<Position> positions) {
        Objects.requireNonNull(grid, "grid must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(positions, "positions must not be null");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        int dim = grid.size().dimension();
        Cell[][] cells = copyCells(grid);
        for (Position position : positions) {
            Objects.requireNonNull(position, "positions must not contain null");
            if (position.row() >= dim || position.col() >= dim) {
                throw new IndexOutOfBoundsException(
                        "position " + position + " out of bounds for grid of size " + dim);
            }
            Cell existing = cells[position.row()][position.col()];
            cells[position.row()][position.col()] =
                    new Cell(position, symbol, existing.prizeValue());
        }
        return new Grid(grid.size(), cells);
    }

    /**
     * Reports whether {@code evaluator} finds a winning pattern in {@code grid}.
     *
     * <p>A safety check for loser tickets: after filling remaining cells, confirm the fill did not
     * accidentally create a win.
     *
     * @param grid      the grid to inspect; never {@code null}
     * @param evaluator the evaluator to run; never {@code null}
     * @return {@code true} if the grid evaluates to a winner
     */
    public boolean hasAccidentalWin(Grid grid, WinEvaluator evaluator) {
        Objects.requireNonNull(grid, "grid must not be null");
        Objects.requireNonNull(evaluator, "evaluator must not be null");
        return evaluator.evaluate(grid).isWinner();
    }

    private static List<String> candidates(List<String> pool, Set<String> exclude) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String symbol : pool) {
            Objects.requireNonNull(symbol, "pool must not contain null");
            if (!EMPTY.equals(symbol) && !exclude.contains(symbol)) {
                distinct.add(symbol);
            }
        }
        return new ArrayList<>(distinct);
    }

    private static Cell[][] copyCells(Grid grid) {
        int dim = grid.size().dimension();
        Cell[][] cells = new Cell[dim][dim];
        for (int row = 0; row < dim; row++) {
            for (int col = 0; col < dim; col++) {
                cells[row][col] = grid.getCell(row, col);
            }
        }
        return cells;
    }
}
