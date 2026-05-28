package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookStatsTest {

    private static final UUID BOOK_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void shouldConstructValidBookStats_andExposeAccessors() {
        BookStats stats = new BookStats(
                BOOK_ID,
                30,
                new BigDecimal("3000.00"),
                new BigDecimal("1950.00"),
                new BigDecimal("0.65"));

        assertThat(stats.bookId()).isEqualTo(BOOK_ID);
        assertThat(stats.ticketsBought()).isEqualTo(30);
        assertThat(stats.totalSpent()).isEqualByComparingTo("3000.00");
        assertThat(stats.totalWon()).isEqualByComparingTo("1950.00");
        assertThat(stats.returnRate()).isEqualByComparingTo("0.65");
    }

    @Test
    void shouldAcceptReturnRateAboveOne_forLuckyBook() {
        BookStats stats = new BookStats(
                BOOK_ID,
                3,
                new BigDecimal("30.00"),
                new BigDecimal("60.00"),
                new BigDecimal("2.00"));

        assertThat(stats.returnRate()).isEqualByComparingTo("2.00");
    }

    @Test
    void shouldAcceptZeroValues() {
        BookStats stats = new BookStats(
                BOOK_ID,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(stats.ticketsBought()).isZero();
        assertThat(stats.totalWon()).isEqualByComparingTo("0");
    }

    @Test
    void shouldRejectNullBookId() {
        assertThatThrownBy(() -> new BookStats(
                null,
                1,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bookId");
    }

    @Test
    void shouldRejectNegativeTicketsBought() {
        assertThatThrownBy(() -> new BookStats(
                BOOK_ID,
                -1,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticketsBought");
    }

    @Test
    void shouldRejectNullTotalSpent() {
        assertThatThrownBy(() -> new BookStats(
                BOOK_ID,
                1,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("totalSpent");
    }

    @Test
    void shouldRejectNegativeTotalSpent() {
        assertThatThrownBy(() -> new BookStats(
                BOOK_ID,
                1,
                new BigDecimal("-1"),
                BigDecimal.ONE,
                BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalSpent");
    }

    @Test
    void shouldRejectNullTotalWon() {
        assertThatThrownBy(() -> new BookStats(
                BOOK_ID,
                1,
                BigDecimal.ONE,
                null,
                BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("totalWon");
    }

    @Test
    void shouldRejectNegativeTotalWon() {
        assertThatThrownBy(() -> new BookStats(
                BOOK_ID,
                1,
                BigDecimal.ONE,
                new BigDecimal("-0.01"),
                BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalWon");
    }

    @Test
    void shouldRejectNullReturnRate() {
        assertThatThrownBy(() -> new BookStats(
                BOOK_ID,
                1,
                BigDecimal.ONE,
                BigDecimal.ONE,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("returnRate");
    }

    @Test
    void shouldRejectNegativeReturnRate() {
        assertThatThrownBy(() -> new BookStats(
                BOOK_ID,
                1,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("-2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("returnRate");
    }
}
