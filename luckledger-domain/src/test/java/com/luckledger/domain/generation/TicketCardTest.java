package com.luckledger.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.theme.AssetRef;
import com.luckledger.domain.generation.theme.CoatingConfig;
import com.luckledger.domain.generation.theme.ColorPalette;
import com.luckledger.domain.generation.theme.ThemeRef;
import com.luckledger.domain.generation.theme.ThemedCell;
import com.luckledger.domain.generation.theme.ThemedGrid;
import com.luckledger.domain.generation.theme.ThemedSymbol;
import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.mechanic.Position;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketCardTest {

    private static final int DIM = GridSize.THREE.dimension();

    private static TicketLayout layout() {
        Cell[][] cells = new Cell[DIM][DIM];
        for (int r = 0; r < DIM; r++) {
            for (int c = 0; c < DIM; c++) {
                cells[r][c] = new Cell(new Position(r, c), "X", 0.0);
            }
        }
        return new TicketLayout(UUID.randomUUID(), new Grid(GridSize.THREE, cells), MechanicType.DEMON_SEAL);
    }

    private static ThemedGrid themedGrid() {
        ThemedSymbol sym = new ThemedSymbol("X", "🤠", null, "Hat");
        ThemedCell[][] cells = new ThemedCell[DIM][DIM];
        for (int r = 0; r < DIM; r++) {
            for (int c = 0; c < DIM; c++) {
                cells[r][c] = new ThemedCell(new Position(r, c), sym);
            }
        }
        return new ThemedGrid(GridSize.THREE, cells);
    }

    private static ThemeRef theme() {
        return new ThemeRef(
                "texas",
                "Texas",
                Map.of("X", new ThemedSymbol("X", "🤠", null, "Hat")),
                new ColorPalette("#1", "#2", "#3", "#4", "#5"),
                new AssetRef("/bg.png"),
                new CoatingConfig("#C4A535", java.util.List.of("#1", "#2"), 0.6, 45, 5),
                null);
    }

    @Test
    void holdsItsComponents() {
        TicketLayout layout = layout();
        ThemedGrid grid = themedGrid();
        ThemeRef theme = theme();
        UUID id = UUID.randomUUID();

        TicketCard card = new TicketCard(id, layout, grid, theme);

        assertThat(card.ticketId()).isEqualTo(id);
        assertThat(card.layout()).isSameAs(layout);
        assertThat(card.skinnedGrid()).isSameAs(grid);
        assertThat(card.theme()).isSameAs(theme);
    }

    @Test
    void nullComponentsAreRejected() {
        assertThatThrownBy(() -> new TicketCard(null, layout(), themedGrid(), theme()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketCard(UUID.randomUUID(), null, themedGrid(), theme()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketCard(UUID.randomUUID(), layout(), null, theme()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketCard(UUID.randomUUID(), layout(), themedGrid(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
