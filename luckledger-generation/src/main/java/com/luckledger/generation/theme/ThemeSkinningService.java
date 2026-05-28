package com.luckledger.generation.theme;

import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.generation.TicketLayout;
import com.luckledger.domain.generation.theme.ThemeRef;
import com.luckledger.domain.generation.theme.ThemedCell;
import com.luckledger.domain.generation.theme.ThemedGrid;
import com.luckledger.domain.generation.theme.ThemedSymbol;
import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Layer 4 of the generation pipeline: skins an abstract {@link TicketLayout} into a renderable
 * {@link TicketCard} by mapping every grid symbol through a {@link ThemeRef}'s symbol map. Theming is
 * purely cosmetic — it changes how a cell looks, never the outcome the mechanic already encoded.
 *
 * <p>The available themes are constructor-injected (the registry); the service does not invent or
 * load theme data itself, so the data source — built-in defaults, a config file, a database — is the
 * caller's choice. {@link #getAvailableThemes()} and {@link #getTheme(String)} expose that registry.
 */
public final class ThemeSkinningService {

    private final Map<String, ThemeRef> themesById;

    /**
     * @param themes the registry of available themes, keyed by {@link ThemeRef#themeId()}; never
     *     {@code null} and never containing {@code null} elements. Duplicate ids resolve to the last
     *     entry.
     */
    public ThemeSkinningService(List<ThemeRef> themes) {
        Objects.requireNonNull(themes, "themes must not be null");
        Map<String, ThemeRef> byId = new LinkedHashMap<>();
        for (ThemeRef theme : themes) {
            Objects.requireNonNull(theme, "themes must not contain null elements");
            byId.put(theme.themeId(), theme);
        }
        this.themesById = Collections.unmodifiableMap(byId);
    }

    /**
     * Skins a layout with the given theme, producing a fully renderable ticket.
     *
     * @param layout the abstract layout (outcome + mechanic grid); never {@code null}
     * @param theme the theme to apply; never {@code null}. Its symbol map must contain every abstract
     *     symbol present in the layout's grid.
     * @return a {@link TicketCard} with a fresh {@code ticketId}, the themed grid, and the theme
     * @throws IllegalArgumentException if the theme has no mapping for a symbol on the grid
     */
    public TicketCard skin(TicketLayout layout, ThemeRef theme) {
        Objects.requireNonNull(layout, "layout must not be null");
        Objects.requireNonNull(theme, "theme must not be null");

        Grid grid = layout.grid();
        int dimension = grid.size().dimension();
        ThemedCell[][] cells = new ThemedCell[dimension][dimension];
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                Cell cell = grid.getCell(row, col);
                ThemedSymbol symbol = theme.symbolMap().get(cell.symbol());
                if (symbol == null) {
                    throw new IllegalArgumentException(
                            "theme '" + theme.themeId() + "' has no mapping for symbol '" + cell.symbol() + "'");
                }
                cells[row][col] = new ThemedCell(cell.position(), symbol);
            }
        }
        ThemedGrid skinnedGrid = new ThemedGrid(grid.size(), cells);
        return new TicketCard(UUID.randomUUID(), layout, skinnedGrid, theme);
    }

    /** The registered themes, in insertion order. */
    public List<ThemeRef> getAvailableThemes() {
        return List.copyOf(themesById.values());
    }

    /**
     * Looks up a registered theme by id.
     *
     * @param themeId the theme id; never {@code null}
     * @return the theme
     * @throws IllegalArgumentException if no theme is registered with that id
     */
    public ThemeRef getTheme(String themeId) {
        Objects.requireNonNull(themeId, "themeId must not be null");
        ThemeRef theme = themesById.get(themeId);
        if (theme == null) {
            throw new IllegalArgumentException("no theme registered with id '" + themeId + "'");
        }
        return theme;
    }
}
