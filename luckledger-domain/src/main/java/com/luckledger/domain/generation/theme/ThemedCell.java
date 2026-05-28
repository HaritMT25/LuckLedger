package com.luckledger.domain.generation.theme;

import com.luckledger.domain.mechanic.Position;
import java.util.Objects;

/**
 * One cell of a themed grid: an abstract {@link Position} paired with the {@link ThemedSymbol} the
 * theme renders there. It carries presentation only — the prize and scoring live on the underlying
 * mechanic {@code Cell}; theming never changes the outcome.
 *
 * @param position the cell's grid coordinate; non-null
 * @param themedSymbol the visual rendered at that coordinate; non-null
 */
public record ThemedCell(Position position, ThemedSymbol themedSymbol) {

    public ThemedCell {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(themedSymbol, "themedSymbol must not be null");
    }
}
