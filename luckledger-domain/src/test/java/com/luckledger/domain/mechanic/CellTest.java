package com.luckledger.domain.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CellTest {

    @Test
    void shouldExposePositionSymbolAndPrizeValue() {
        Position position = new Position(1, 2);

        Cell cell = new Cell(position, "CHERRY", 50.0);

        assertThat(cell.position()).isEqualTo(position);
        assertThat(cell.symbol()).isEqualTo("CHERRY");
        assertThat(cell.prizeValue()).isEqualTo(50.0);
    }

    @Test
    void shouldAcceptZeroPrizeValueForLoserCell() {
        Cell cell = new Cell(new Position(0, 0), "LEMON", 0.0);

        assertThat(cell.prizeValue()).isZero();
    }

    @Test
    void shouldRejectNullPosition() {
        assertThatThrownBy(() -> new Cell(null, "CHERRY", 10.0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullSymbol() {
        assertThatThrownBy(() -> new Cell(new Position(0, 0), null, 10.0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectBlankSymbol() {
        assertThatThrownBy(() -> new Cell(new Position(0, 0), "   ", 10.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativePrizeValue() {
        assertThatThrownBy(() -> new Cell(new Position(0, 0), "CHERRY", -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
