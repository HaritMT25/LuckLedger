package com.luckledger.domain.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void shouldExposeRowAndColumnForValidCoordinates() {
        Position position = new Position(2, 4);

        assertThat(position.row()).isEqualTo(2);
        assertThat(position.col()).isEqualTo(4);
    }

    @Test
    void shouldAcceptOriginCoordinates() {
        Position position = new Position(0, 0);

        assertThat(position.row()).isZero();
        assertThat(position.col()).isZero();
    }

    @Test
    void shouldRejectNegativeRow() {
        assertThatThrownBy(() -> new Position(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeColumn() {
        assertThatThrownBy(() -> new Position(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
