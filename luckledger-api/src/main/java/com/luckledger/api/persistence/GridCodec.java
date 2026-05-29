package com.luckledger.api.persistence;

import com.luckledger.domain.generation.theme.ThemedCell;
import com.luckledger.domain.generation.theme.ThemedGrid;
import com.luckledger.domain.generation.theme.ThemedSymbol;
import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts mechanic and themed grids to a stable, JSON-friendly DTO shape for storage in {@code jsonb}
 * columns ({@link TicketEntity#getGrid()} / {@link TicketEntity#getSkinnedGrid()}). Hibernate
 * (via {@code @JdbcTypeCode(SqlTypes.JSON)}) serializes these records into the column and the API
 * serves the themed grid to the frontend as-is.
 *
 * <p>By design a reveal trusts the ticket's persisted {@code prize_amount} (already verified at
 * generation time) rather than reconstructing a domain {@link Grid} from this DTO and re-evaluating
 * it — so these records are for storage and display only; no domain grid is ever rebuilt from them.
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
