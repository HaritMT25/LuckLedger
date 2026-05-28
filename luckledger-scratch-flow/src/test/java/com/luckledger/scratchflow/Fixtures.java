package com.luckledger.scratchflow;

import com.luckledger.distribution.TicketBook;
import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.generation.TicketLayout;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Minimal scratch-flow fixtures: tickets and books whose grids are inert (services don't read them
 *  in these tests — reveal uses a stub evaluator). */
final class Fixtures {

    private static final int DIM = GridSize.THREE.dimension();
    private static final ThemedSymbol SYMBOL = new ThemedSymbol("X", "x", null, "X");
    private static final ThemeRef THEME = new ThemeRef(
            "t", "T", Map.of("X", SYMBOL),
            new ColorPalette("#1", "#2", "#3", "#4", "#5"),
            new AssetRef("/bg.png"),
            new CoatingConfig("#1", List.of("#1"), 0.5, 0, 1),
            null);

    private Fixtures() {}

    static TicketCard card(double prize) {
        Cell[][] cells = new Cell[DIM][DIM];
        ThemedCell[][] themed = new ThemedCell[DIM][DIM];
        for (int r = 0; r < DIM; r++) {
            for (int c = 0; c < DIM; c++) {
                cells[r][c] = new Cell(new Position(r, c), "X", 0.0);
                themed[r][c] = new ThemedCell(new Position(r, c), SYMBOL);
            }
        }
        TicketLayout layout = new TicketLayout(
                UUID.randomUUID(), new Grid(GridSize.THREE, cells), MechanicType.DEMON_SEAL, BigDecimal.valueOf(prize));
        return new TicketCard(UUID.randomUUID(), layout, new ThemedGrid(GridSize.THREE, themed), THEME);
    }

    static List<TicketCard> cards(int count) {
        List<TicketCard> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(card(0));
        }
        return list;
    }

    static TicketBook book(int ticketCount) {
        return new TicketBook(UUID.randomUUID(), cards(ticketCount), UUID.randomUUID());
    }
}
