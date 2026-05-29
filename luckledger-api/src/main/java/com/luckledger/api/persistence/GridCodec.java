package com.luckledger.api.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luckledger.domain.generation.theme.ThemedCell;
import com.luckledger.domain.generation.theme.ThemedGrid;
import com.luckledger.domain.generation.theme.ThemedSymbol;
import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes mechanic and themed grids to a stable JSON shape for storage in {@code jsonb} columns.
 *
 * <p>Serialize-only by design: the mechanic grid is stored for audit/display, and a reveal trusts the
 * ticket's persisted {@code prize_amount} (already verified at generation time) rather than
 * re-evaluating a deserialized grid — so no fragile grid deserialization is needed. The themed grid
 * is served to the frontend as-is.
 */
public final class GridCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GridCodec() {}

    public static String toJson(Grid grid) {
        List<CellDto> cells = new ArrayList<>();
        for (Cell cell : grid.getAllCells()) {
            cells.add(new CellDto(cell.position().row(), cell.position().col(), cell.symbol(), cell.prizeValue()));
        }
        return write(new GridDto(grid.size().name(), grid.size().dimension(), cells));
    }

    public static String toJson(ThemedGrid grid) {
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
        return write(new ThemedGridDto(grid.size().name(), grid.size().dimension(), cells));
    }

    private static String write(Object dto) {
        try {
            return MAPPER.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize grid to JSON", e);
        }
    }

    public record GridDto(String size, int dimension, List<CellDto> cells) {}

    public record CellDto(int row, int col, String symbol, double prizeValue) {}

    public record ThemedGridDto(String size, int dimension, List<ThemedCellDto> cells) {}

    public record ThemedCellDto(
            int row, int col, String abstractSymbol, String displayEmoji, String displayImageUrl, String displayLabel) {}
}
