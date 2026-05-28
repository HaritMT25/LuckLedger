package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CurvePointTest {

    @Test
    void shouldConstructValidCurvePoint_andExposeAccessors() {
        CurvePoint point = new CurvePoint(
                10,
                new BigDecimal("1000.00"),
                new BigDecimal("650.00"),
                new BigDecimal("-350.00"));

        assertThat(point.ticketNumber()).isEqualTo(10);
        assertThat(point.cumulativeSpent()).isEqualByComparingTo("1000.00");
        assertThat(point.cumulativeWon()).isEqualByComparingTo("650.00");
        assertThat(point.netPosition()).isEqualByComparingTo("-350.00");
    }

    @Test
    void shouldAcceptNegativeNetPosition_theCommonCase() {
        CurvePoint point = new CurvePoint(
                25,
                new BigDecimal("2500.00"),
                new BigDecimal("1600.00"),
                new BigDecimal("-900.00"));

        assertThat(point.netPosition()).isEqualByComparingTo("-900.00");
        assertThat(point.netPosition()).isNegative();
    }

    @Test
    void shouldAcceptPositiveNetPosition_whenPlayerIsAhead() {
        CurvePoint point = new CurvePoint(
                1,
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                new BigDecimal("40.00"));

        assertThat(point.netPosition()).isEqualByComparingTo("40.00");
    }

    @Test
    void shouldAcceptTicketNumberOne_asLowerBound() {
        CurvePoint point = new CurvePoint(
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(point.ticketNumber()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void shouldRejectTicketNumberBelowOne(int ticketNumber) {
        assertThatThrownBy(() -> new CurvePoint(
                ticketNumber,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticketNumber");
    }

    @Test
    void shouldRejectNullCumulativeSpent() {
        assertThatThrownBy(() -> new CurvePoint(
                1,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cumulativeSpent");
    }

    @Test
    void shouldRejectNegativeCumulativeSpent() {
        assertThatThrownBy(() -> new CurvePoint(
                1,
                new BigDecimal("-1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cumulativeSpent");
    }

    @Test
    void shouldRejectNullCumulativeWon() {
        assertThatThrownBy(() -> new CurvePoint(
                1,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cumulativeWon");
    }

    @Test
    void shouldRejectNegativeCumulativeWon() {
        assertThatThrownBy(() -> new CurvePoint(
                1,
                BigDecimal.ZERO,
                new BigDecimal("-0.01"),
                BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cumulativeWon");
    }

    @Test
    void shouldRejectNullNetPosition() {
        assertThatThrownBy(() -> new CurvePoint(
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("netPosition");
    }
}
