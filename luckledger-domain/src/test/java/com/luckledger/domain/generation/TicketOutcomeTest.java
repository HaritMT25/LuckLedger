package com.luckledger.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketOutcomeTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void holdsItsIdAndPrize() {
        TicketOutcome outcome = new TicketOutcome(ID, new BigDecimal("25"));

        assertThat(outcome.outcomeId()).isEqualTo(ID);
        assertThat(outcome.prizeAmount()).isEqualByComparingTo("25");
    }

    @Test
    void nullIdIsRejected() {
        assertThatThrownBy(() -> new TicketOutcome(null, BigDecimal.TEN))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullPrizeIsRejected() {
        assertThatThrownBy(() -> new TicketOutcome(ID, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void negativePrizeIsRejected() {
        assertThatThrownBy(() -> new TicketOutcome(ID, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroPrizeIsALoserNotAWinner() {
        TicketOutcome outcome = new TicketOutcome(ID, BigDecimal.ZERO);

        assertThat(outcome.isWinner()).isFalse();
        assertThat(outcome.isLoser()).isTrue();
    }

    @Test
    void positivePrizeIsAWinnerWhenFloorIsZero() {
        TicketOutcome outcome = new TicketOutcome(ID, new BigDecimal("0.01"));

        assertThat(outcome.isWinner()).isTrue();
        assertThat(outcome.isLoser()).isFalse();
    }

    @Test
    void prizeEqualToMinPayoutIsALoser() {
        TicketOutcome outcome = new TicketOutcome(ID, new BigDecimal("5"));
        BigDecimal minPayout = new BigDecimal("5");

        assertThat(outcome.isWinner(minPayout)).isFalse();
        assertThat(outcome.isLoser(minPayout)).isTrue();
    }

    @Test
    void prizeAboveMinPayoutIsAWinner() {
        TicketOutcome outcome = new TicketOutcome(ID, new BigDecimal("6"));
        BigDecimal minPayout = new BigDecimal("5");

        assertThat(outcome.isWinner(minPayout)).isTrue();
        assertThat(outcome.isLoser(minPayout)).isFalse();
    }

    @Test
    void scaleDoesNotAffectMinPayoutComparison() {
        // 5.00 vs 5 must compare equal (BigDecimal.compareTo, not equals).
        TicketOutcome outcome = new TicketOutcome(ID, new BigDecimal("5.00"));

        assertThat(outcome.isLoser(new BigDecimal("5"))).isTrue();
        assertThat(outcome.isWinner(new BigDecimal("5"))).isFalse();
    }

    @Test
    void nullMinPayoutIsRejected() {
        TicketOutcome outcome = new TicketOutcome(ID, BigDecimal.TEN);

        assertThatThrownBy(() -> outcome.isWinner(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> outcome.isLoser(null)).isInstanceOf(NullPointerException.class);
    }
}
