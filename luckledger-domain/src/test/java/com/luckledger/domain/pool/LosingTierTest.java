package com.luckledger.domain.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LosingTierTest {

    @Test
    void shouldConstructLosingTierWithZeroValue() {
        LosingTier tier = new LosingTier(BigDecimal.ZERO, 100, "Better luck next time");

        assertThat(tier.value()).isEqualByComparingTo("0");
        assertThat(tier.count()).isEqualTo(100);
        assertThat(tier.consolationMessage()).isEqualTo("Better luck next time");
    }

    @Test
    void shouldConstructLosingTierWithFloorValue() {
        LosingTier tier = new LosingTier(new BigDecimal("1.00"), 33, "You get a free play");

        assertThat(tier.value()).isEqualByComparingTo("1.00");
        assertThat(tier.count()).isEqualTo(33);
    }

    @Test
    void shouldComputeTierCostAsValueTimesCount() {
        LosingTier tier = new LosingTier(new BigDecimal("1.00"), 33, "Floor");

        assertThat(tier.getTierCost()).isEqualByComparingTo("33.00");
    }

    @Test
    void shouldComputeZeroTierCostWhenNoFloor() {
        LosingTier tier = new LosingTier(BigDecimal.ZERO, 3500, "Loser");

        assertThat(tier.getTierCost()).isEqualByComparingTo("0");
    }

    @Test
    void shouldAllowZeroCountWhenEveryTicketWins() {
        LosingTier tier = new LosingTier(BigDecimal.ZERO, 0, "No losers");

        assertThat(tier.count()).isZero();
        assertThat(tier.getTierCost()).isEqualByComparingTo("0");
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> new LosingTier(null, 1, "x"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNegativeValue() {
        assertThatThrownBy(() -> new LosingTier(new BigDecimal("-1"), 1, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -100})
    void shouldRejectNegativeCount(int count) {
        assertThatThrownBy(() -> new LosingTier(BigDecimal.ZERO, count, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    @Test
    void shouldRejectNullConsolationMessage() {
        assertThatThrownBy(() -> new LosingTier(BigDecimal.ZERO, 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void shouldRejectBlankConsolationMessage(String message) {
        assertThatThrownBy(() -> new LosingTier(BigDecimal.ZERO, 1, message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consolationMessage");
    }

    @Test
    void shouldDeriveCountFromTotalTicketsMinusSingleWinningTier() {
        List<PrizeTier> winners = List.of(new PrizeTier(new BigDecimal("2"), 2360, "2 matches"));

        LosingTier tier = LosingTier.derive(10000, winners, BigDecimal.ZERO, "Loser");

        assertThat(tier.count()).isEqualTo(7640);
    }

    @Test
    void shouldDeriveCountFromTotalTicketsMinusMultipleWinningTiers() {
        List<PrizeTier> winners = List.of(
                new PrizeTier(new BigDecimal("740"), 25, "JACKPOT"),
                new PrizeTier(new BigDecimal("20"), 450, "3 matches"),
                new PrizeTier(new BigDecimal("2"), 2360, "2 matches"));

        LosingTier tier = LosingTier.derive(10000, winners, BigDecimal.ZERO, "Loser");

        assertThat(tier.count()).isEqualTo(7165);
    }

    @Test
    void shouldDeriveZeroCountWhenWinnersFillEntirePool() {
        List<PrizeTier> winners = List.of(new PrizeTier(new BigDecimal("1"), 10000, "Everyone wins"));

        LosingTier tier = LosingTier.derive(10000, winners, BigDecimal.ZERO, "Unused");

        assertThat(tier.count()).isZero();
    }

    @Test
    void shouldCarryFloorValueAndMessageThroughDerive() {
        List<PrizeTier> winners = List.of(new PrizeTier(new BigDecimal("10"), 100, "Win"));

        LosingTier tier = LosingTier.derive(200, winners, new BigDecimal("1.00"), "Free play");

        assertThat(tier.value()).isEqualByComparingTo("1.00");
        assertThat(tier.count()).isEqualTo(100);
        assertThat(tier.consolationMessage()).isEqualTo("Free play");
    }

    @Test
    void shouldThrowWhenWinningTierCountsExceedTotalTickets() {
        List<PrizeTier> winners = List.of(new PrizeTier(new BigDecimal("2"), 12000, "Too many"));

        assertThatThrownBy(() -> LosingTier.derive(10000, winners, BigDecimal.ZERO, "Loser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    @Test
    void shouldRejectNullWinningTiersInDerive() {
        assertThatThrownBy(() -> LosingTier.derive(10000, null, BigDecimal.ZERO, "Loser"))
                .isInstanceOf(NullPointerException.class);
    }
}
