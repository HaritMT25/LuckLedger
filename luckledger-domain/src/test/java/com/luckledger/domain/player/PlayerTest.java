package com.luckledger.domain.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Player newPlayer() {
        return new Player(PLAYER_ID, "Ada");
    }

    @Test
    void newPlayerStartsEmpty() {
        Player player = newPlayer();

        assertThat(player.getPlayerId()).isEqualTo(PLAYER_ID);
        assertThat(player.getDisplayName()).isEqualTo("Ada");
        assertThat(player.getCoinBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(player.getTotalBorrowed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(player.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(player.getTotalWon()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(player.getTicketCount()).isZero();
    }

    @Test
    void constructorRejectsNullPlayerId() {
        assertThatThrownBy(() -> new Player(null, "Ada"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNullDisplayName() {
        assertThatThrownBy(() -> new Player(PLAYER_ID, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsBlankDisplayName() {
        assertThatThrownBy(() -> new Player(PLAYER_ID, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordBorrowIncreasesBalanceAndBorrowedTotal() {
        Player player = newPlayer();

        player.recordBorrow(new BigDecimal("100"));

        assertThat(player.getCoinBalance()).isEqualByComparingTo("100");
        assertThat(player.getTotalBorrowed()).isEqualByComparingTo("100");
        assertThat(player.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(player.getTicketCount()).isZero();
    }

    @Test
    void recordBorrowAccumulates() {
        Player player = newPlayer();

        player.recordBorrow(new BigDecimal("100"));
        player.recordBorrow(new BigDecimal("50"));

        assertThat(player.getCoinBalance()).isEqualByComparingTo("150");
        assertThat(player.getTotalBorrowed()).isEqualByComparingTo("150");
    }

    @Test
    void debitReducesBalanceAndIncrementsSpentAndTicketCount() {
        Player player = newPlayer();
        player.recordBorrow(new BigDecimal("100"));

        player.debit(new BigDecimal("5"));

        assertThat(player.getCoinBalance()).isEqualByComparingTo("95");
        assertThat(player.getTotalSpent()).isEqualByComparingTo("5");
        assertThat(player.getTotalBorrowed()).isEqualByComparingTo("100");
        assertThat(player.getTicketCount()).isEqualTo(1);
    }

    @Test
    void debitThrowsWhenBalanceInsufficient() {
        Player player = newPlayer();
        player.recordBorrow(new BigDecimal("3"));

        assertThatThrownBy(() -> player.debit(new BigDecimal("5")))
                .isInstanceOf(InsufficientBalanceException.class)
                .extracting("playerId", "currentBalance", "requestedAmount")
                .containsExactly(PLAYER_ID, new BigDecimal("3"), new BigDecimal("5"));

        assertThat(player.getCoinBalance()).isEqualByComparingTo("3");
        assertThat(player.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(player.getTicketCount()).isZero();
    }

    @Test
    void debitExactBalanceSucceeds() {
        Player player = newPlayer();
        player.recordBorrow(new BigDecimal("5"));

        player.debit(new BigDecimal("5"));

        assertThat(player.getCoinBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(player.getTotalSpent()).isEqualByComparingTo("5");
    }

    @Test
    void creditIncreasesBalanceAndWonTotal() {
        Player player = newPlayer();
        player.recordBorrow(new BigDecimal("10"));
        player.debit(new BigDecimal("5"));

        player.credit(new BigDecimal("20"));

        assertThat(player.getCoinBalance()).isEqualByComparingTo("25");
        assertThat(player.getTotalWon()).isEqualByComparingTo("20");
        assertThat(player.getTicketCount()).isEqualTo(1);
    }

    @Test
    void getNetPositionIsWonMinusSpent() {
        Player player = newPlayer();
        player.recordBorrow(new BigDecimal("100"));
        player.debit(new BigDecimal("60"));
        player.credit(new BigDecimal("25"));

        assertThat(player.getNetPosition()).isEqualByComparingTo("-35");
    }

    @Test
    void getRollingReturnRateIsWonOverSpent() {
        Player player = newPlayer();
        player.recordBorrow(new BigDecimal("1000"));
        player.debit(new BigDecimal("500"));
        player.credit(new BigDecimal("325"));

        assertThat(player.getRollingReturnRate()).isEqualByComparingTo("0.65");
    }

    @Test
    void getRollingReturnRateIsZeroWhenNothingSpent() {
        Player player = newPlayer();

        assertThat(player.getRollingReturnRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void canAffordComparesAgainstBalance() {
        Player player = newPlayer();
        player.recordBorrow(new BigDecimal("10"));

        assertThat(player.canAfford(new BigDecimal("10"))).isTrue();
        assertThat(player.canAfford(new BigDecimal("9.99"))).isTrue();
        assertThat(player.canAfford(new BigDecimal("10.01"))).isFalse();
    }

    @Test
    void debitRejectsNonPositiveAmount() {
        Player player = newPlayer();
        player.recordBorrow(new BigDecimal("10"));

        assertThatThrownBy(() -> player.debit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> player.debit(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creditRejectsNonPositiveAmount() {
        Player player = newPlayer();

        assertThatThrownBy(() -> player.credit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> player.credit(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordBorrowRejectsNonPositiveAmount() {
        Player player = newPlayer();

        assertThatThrownBy(() -> player.recordBorrow(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> player.recordBorrow(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mutatingMethodsRejectNullAmount() {
        Player player = newPlayer();

        assertThatThrownBy(() -> player.debit(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> player.credit(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> player.recordBorrow(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> player.canAfford(null)).isInstanceOf(NullPointerException.class);
    }
}
