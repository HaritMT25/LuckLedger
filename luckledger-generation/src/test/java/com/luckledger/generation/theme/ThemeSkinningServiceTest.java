package com.luckledger.generation.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.generation.TicketLayout;
import com.luckledger.domain.generation.theme.AssetRef;
import com.luckledger.domain.generation.theme.CoatingConfig;
import com.luckledger.domain.generation.theme.ColorPalette;
import com.luckledger.domain.generation.theme.ThemeRef;
import com.luckledger.domain.generation.theme.ThemedSymbol;
import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.mechanic.Position;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThemeSkinningServiceTest {

    private static final int DIM = GridSize.THREE.dimension();

    /** A 3x3 layout filled with the single abstract symbol "A". */
    private static TicketLayout layout(String symbol) {
        Cell[][] cells = new Cell[DIM][DIM];
        for (int r = 0; r < DIM; r++) {
            for (int c = 0; c < DIM; c++) {
                cells[r][c] = new Cell(new Position(r, c), symbol, 0.0);
            }
        }
        return new TicketLayout(
                UUID.randomUUID(), new Grid(GridSize.THREE, cells), MechanicType.CELESTIAL_FORTUNE);
    }

    private static ThemeRef theme(String id, Map<String, ThemedSymbol> symbols) {
        return new ThemeRef(
                id,
                "Theme " + id,
                symbols,
                new ColorPalette("#1", "#2", "#3", "#4", "#5"),
                new AssetRef("/bg.png"),
                new CoatingConfig("#C4A535", List.of("#1", "#2"), 0.6, 45, 5),
                null);
    }

    private static final ThemedSymbol SYM_A = new ThemedSymbol("A", "🤠", null, "Cowboy");
    private static final ThemeRef TEXAS = theme("texas", Map.of("A", SYM_A));

    @Test
    void skinsEveryCellThroughTheSymbolMap() {
        ThemeSkinningService service = new ThemeSkinningService(List.of(TEXAS));

        TicketCard card = service.skin(layout("A"), TEXAS);

        assertThat(card.skinnedGrid().size()).isEqualTo(GridSize.THREE);
        assertThat(card.skinnedGrid().getCell(0, 0).themedSymbol()).isEqualTo(SYM_A);
        assertThat(card.skinnedGrid().getAllCells()).allSatisfy(c -> assertThat(c.themedSymbol()).isEqualTo(SYM_A));
    }

    @Test
    void preservesLayoutAndThemeAndAssignsFreshTicketId() {
        ThemeSkinningService service = new ThemeSkinningService(List.of(TEXAS));
        TicketLayout layout = layout("A");

        TicketCard card = service.skin(layout, TEXAS);

        assertThat(card.layout()).isSameAs(layout);
        assertThat(card.theme()).isSameAs(TEXAS);
        assertThat(card.ticketId()).isNotNull();
        // a second skin of the same layout gets a distinct ticket id
        assertThat(service.skin(layout, TEXAS).ticketId()).isNotEqualTo(card.ticketId());
    }

    @Test
    void unmappedSymbolThrows() {
        ThemeSkinningService service = new ThemeSkinningService(List.of(TEXAS));

        assertThatThrownBy(() -> service.skin(layout("UNKNOWN"), TEXAS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void getAvailableThemesReturnsTheRegistry() {
        ThemeRef other = theme("vegas", Map.of("A", SYM_A));
        ThemeSkinningService service = new ThemeSkinningService(List.of(TEXAS, other));

        assertThat(service.getAvailableThemes()).containsExactly(TEXAS, other);
    }

    @Test
    void getThemeLooksUpById() {
        ThemeSkinningService service = new ThemeSkinningService(List.of(TEXAS));

        assertThat(service.getTheme("texas")).isSameAs(TEXAS);
        assertThatThrownBy(() -> service.getTheme("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullThemesInRegistryAreRejected() {
        assertThatThrownBy(() -> new ThemeSkinningService(null))
                .isInstanceOf(NullPointerException.class);
    }
}
