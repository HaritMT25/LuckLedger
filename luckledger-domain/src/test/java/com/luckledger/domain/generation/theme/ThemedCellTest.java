package com.luckledger.domain.generation.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.mechanic.Position;
import org.junit.jupiter.api.Test;

class ThemedCellTest {

    private static final ThemedSymbol SYMBOL = new ThemedSymbol("SYM_A", "🤠", null, "Cowboy Hat");

    @Test
    void holdsPositionAndSymbol() {
        Position position = new Position(1, 2);
        ThemedCell cell = new ThemedCell(position, SYMBOL);

        assertThat(cell.position()).isEqualTo(position);
        assertThat(cell.themedSymbol()).isEqualTo(SYMBOL);
    }

    @Test
    void nullPositionIsRejected() {
        assertThatThrownBy(() -> new ThemedCell(null, SYMBOL)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullSymbolIsRejected() {
        assertThatThrownBy(() -> new ThemedCell(new Position(0, 0), null))
                .isInstanceOf(NullPointerException.class);
    }
}
