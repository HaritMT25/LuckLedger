package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BookValueStatsTest {

    @Test
    void holdsItsFields() {
        BookValueStats stats = new BookValueStats(10.0, 50.0, 30.0, 14.14, 28.0);

        assertThat(stats.min()).isEqualTo(10.0);
        assertThat(stats.max()).isEqualTo(50.0);
        assertThat(stats.mean()).isEqualTo(30.0);
        assertThat(stats.stddev()).isEqualTo(14.14);
        assertThat(stats.median()).isEqualTo(28.0);
    }

    @Test
    void zeroSpreadIsValid() {
        BookValueStats stats = new BookValueStats(5.0, 5.0, 5.0, 0.0, 5.0);

        assertThat(stats.stddev()).isZero();
    }

    @Test
    void maxBelowMinIsRejected() {
        assertThatThrownBy(() -> new BookValueStats(50.0, 10.0, 30.0, 1.0, 30.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeOrNonFiniteValuesAreRejected() {
        assertThatThrownBy(() -> new BookValueStats(-1.0, 10.0, 5.0, 1.0, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookValueStats(0.0, 10.0, 5.0, -1.0, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookValueStats(0.0, Double.NaN, 5.0, 1.0, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
