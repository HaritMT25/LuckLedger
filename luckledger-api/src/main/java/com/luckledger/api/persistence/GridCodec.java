package com.luckledger.api.persistence;

import com.luckledger.domain.generation.theme.ThemedCell;
import com.luckledger.domain.generation.theme.ThemedGrid;
import com.luckledger.domain.generation.theme.ThemedSymbol;
import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts mechanic and themed grids to a stable, JSON-friendly DTO shape for storage in {@code jsonb}
 * columns ({@link TicketEntity#getGrid()} / {@link TicketEntity#getSkinnedGrid()}). Hibernate
 * (via {@code @JdbcTypeCode(SqlTypes.JSON)}) serializes these records into the column and the API
 * serves the themed grid to the frontend as-is.
 *
 * <p>The mechanic grid ({@link GridDto}) now round-trips: {@link #toDto(Grid)} persists it and
 * {@link #toDomain(GridDto)} rebuilds a domain {@link Grid} from it. This two-way conversion is a
 * <strong>read-only, education-layer concern</strong> — it exists so a reveal can produce an
 * explanatory narrative of an <em>already-revealed</em> ticket. It changes nothing about payout:
 * <strong>the reveal always credits the ticket's persisted {@code prize_amount}</strong> (verified at
 * generation time). Any narrator that re-evaluates the rebuilt grid treats a disagreement with the
 * stored prize as a guard trip and emits no narrative — it never becomes a payout.
 */
public final class GridCodec {

    private GridCodec() {}

    /** Maps a mechanic grid to its storage DTO. */
    public static GridDto toDto(Grid grid) {
        List<CellDto> cells = new ArrayList<>();
        for (Cell cell : grid.getAllCells()) {
            cells.add(new CellDto(cell.position().row(), cell.position().col(), cell.symbol(), cell.prizeValue()));
        }
        return new GridDto(grid.size().name(), grid.size().dimension(), cells);
    }

    /**
     * Rebuilds a domain {@link Grid} from its storage DTO — the inverse of {@link #toDto(Grid)}.
     *
     * <p>For the education layer only: the rebuilt grid is re-evaluated to explain an
     * already-revealed ticket, never to decide a payout.
     *
     * @param dto the persisted mechanic grid; never {@code null}, its cells fully populate the square
     * @return the reconstructed grid
     * @throws NullPointerException if {@code dto} is {@code null}
     * @throws IllegalArgumentException if a cell coordinate falls outside the grid or a cell is missing
     */
    public static Grid toDomain(GridDto dto) {
        Objects.requireNonNull(dto, "dto must not be null");
        GridSize size = GridSize.valueOf(dto.size());
        int dimension = size.dimension();
        Cell[][] cells = new Cell[dimension][dimension];
        for (CellDto cell : dto.cells()) {
            if (cell.row() < 0 || cell.row() >= dimension || cell.col() < 0 || cell.col() >= dimension) {
                throw new IllegalArgumentException(
                        "cell (" + cell.row() + ", " + cell.col() + ") out of bounds for grid of size " + dimension);
            }
            cells[cell.row()][cell.col()] =
                    new Cell(new Position(cell.row(), cell.col()), cell.symbol(), cell.prizeValue());
        }
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                if (cells[row][col] == null) {
                    throw new IllegalArgumentException("grid DTO is missing cell (" + row + ", " + col + ")");
                }
            }
        }
        return new Grid(size, cells);
    }

    /** Maps a themed grid to its storage DTO. */
    public static ThemedGridDto toDto(ThemedGrid grid) {
        List<ThemedCellDto> cells = new ArrayList<>();
        for (ThemedCell cell : grid.getAllCells()) {
            ThemedSymbol s = cell.themedSymbol();
            cells.add(new ThemedCellDto(
                    cell.position().row(),
                    cell.position().col(),
                    s.abstractSymbol(),
                    s.displayEmoji(),
                    s.displayImageUrl(),
                    s.displayLabel()));
        }
        return new ThemedGridDto(grid.size().name(), grid.size().dimension(), cells);
    }

    public record GridDto(String size, int dimension, List<CellDto> cells) {}

    public record CellDto(int row, int col, String symbol, double prizeValue) {}

    public record ThemedGridDto(String size, int dimension, List<ThemedCellDto> cells) {}

    public record ThemedCellDto(
            int row, int col, String abstractSymbol, String displayEmoji, String displayImageUrl, String displayLabel) {}
}
