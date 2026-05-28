package com.luckledger.domain.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrizeTierTest {

    @Test
    void shouldConstructValidPrizeTier() {
        PrizeTier tier = new PrizeTier(new BigDecimal("5.00"), 10, "Five");

        assertThat(tier.value()).isEqualByComparingTo("5.00");
        assertThat(tier.count()).isEqualTo(10);
        assertThat(tier.label()).isEqualTo("Five");
    }

    @Test
    void shouldComputeTierCostAsValueTimesCount() {
        PrizeTier tier = new PrizeTier(new BigDecimal("5.00"), 10, "Five");

        assertThat(tier.getTierCost()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldComputeTierCostForSingleCount() {
        PrizeTier tier = new PrizeTier(new BigDecimal("600"), 1, "Jackpot");

        assertThat(tier.getTierCost()).isEqualByComparingTo("600");
    }

    @Test
    void shouldComputeTierCostWithFractionalValue() {
        PrizeTier tier = new PrizeTier(new BigDecimal("2.50"), 4, "Small");

        assertThat(tier.getTierCost()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> new PrizeTier(null, 1, "x"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectZeroValue() {
        assertThatThrownBy(() -> new PrizeTier(BigDecimal.ZERO, 1, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @Test
    void shouldRejectNegativeValue() {
        assertThatThrownBy(() -> new PrizeTier(new BigDecimal("-1"), 1, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void shouldRejectNonPositiveCount(int count) {
        assertThatThrownBy(() -> new PrizeTier(BigDecimal.ONE, count, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    @Test
    void shouldRejectNullLabel() {
        assertThatThrownBy(() -> new PrizeTier(BigDecimal.ONE, 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void shouldRejectBlankLabel(String label) {
        assertThatThrownBy(() -> new PrizeTier(BigDecimal.ONE, 1, label))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }
}
