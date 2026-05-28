package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DealerStatsTest {

    private static final UUID DEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void shouldConstructValidDealerStats_andExposeAccessors() {
        DealerStats stats = new DealerStats(
                DEALER_ID,
                "Joe's Corner Store",
                12,
                new BigDecimal("4800.00"),
                new BigDecimal("3456.00"),
                new BigDecimal("0.72"));

        assertThat(stats.dealerId()).isEqualTo(DEALER_ID);
        assertThat(stats.dealerName()).isEqualTo("Joe's Corner Store");
        assertThat(stats.ticketsBought()).isEqualTo(12);
        assertThat(stats.totalSpent()).isEqualByComparingTo("4800.00");
        assertThat(stats.totalWon()).isEqualByComparingTo("3456.00");
        assertThat(stats.returnRate()).isEqualByComparingTo("0.72");
    }

    @Test
    void shouldAcceptReturnRateAboveOne_forLuckyDealer() {
        DealerStats stats = new DealerStats(
                DEALER_ID,
                "Lucky Downtown",
                5,
                new BigDecimal("100.00"),
                new BigDecimal("250.00"),
                new BigDecimal("2.50"));

        assertThat(stats.returnRate()).isEqualByComparingTo("2.50");
    }

    @Test
    void shouldAcceptZeroValues() {
        DealerStats stats = new DealerStats(
                DEALER_ID,
                "Fresh Dealer",
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(stats.ticketsBought()).isZero();
        assertThat(stats.totalSpent()).isEqualByComparingTo("0");
    }

    @Test
    void shouldRejectNullDealerId() {
        assertThatThrownBy(() -> new DealerStats(
                null,
                "Joe's",
                1,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dealerId");
    }

    @Test
    void shouldRejectNullDealerName() {
        assertThatThrownBy(() -> new DealerStats(
                DEALER_ID,
                null,
                1,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dealerName");
    }

    @Test
    void shouldRejectBlankDealerName() {
        assertThatThrownBy(() -> new DealerStats(
                DEALER_ID,
                "   ",
                1,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dealerName");
    }

    @Test
    void shouldRejectNegativeTicketsBought() {
        assertThatThrownBy(() -> new DealerStats(
                DEALER_ID,
                "Joe's",
                -1,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticketsBought");
    }

    @Test
    void shouldRejectNullTotalSpent() {
        assertThatThrownBy(() -> new DealerStats(
                DEALER_ID,
                "Joe's",
                1,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("totalSpent");
    }

    @Test
    void shouldRejectNegativeTotalSpent() {
        assertThatThrownBy(() -> new DealerStats(
                DEALER_ID,
                "Joe's",
                1,
                new BigDecimal("-0.01"),
                BigDecimal.ONE,
                BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalSpent");
    }

    @Test
    void shouldRejectNullTotalWon() {
        assertThatThrownBy(() -> new DealerStats(
                DEALER_ID,
                "Joe's",
                1,
                BigDecimal.ONE,
                null,
                BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("totalWon");
    }

    @Test
    void shouldRejectNegativeTotalWon() {
        assertThatThrownBy(() -> new DealerStats(
                DEALER_ID,
                "Joe's",
                1,
                BigDecimal.ONE,
                new BigDecimal("-5"),
                BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalWon");
    }

    @Test
    void shouldRejectNullReturnRate() {
        assertThatThrownBy(() -> new DealerStats(
                DEALER_ID,
                "Joe's",
                1,
                BigDecimal.ONE,
                BigDecimal.ONE,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("returnRate");
    }

    @Test
    void shouldRejectNegativeReturnRate() {
        assertThatThrownBy(() -> new DealerStats(
                DEALER_ID,
                "Joe's",
                1,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("-0.10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("returnRate");
    }
}
