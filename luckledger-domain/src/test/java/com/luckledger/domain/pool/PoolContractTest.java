package com.luckledger.domain.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoolContractTest {

    private static PoolContract.Builder validBuilder() {
        return PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.65"))
                .addPrizeTier(new PrizeTier(new BigDecimal("100"), 1, "Jackpot"))
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 5, "Mid"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 20, "Small"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED);
    }

    @Test
    void shouldBuildContractWithAllFields() {
        PoolContract contract = validBuilder().build();

        assertThat(contract.totalTickets()).isEqualTo(100);
        assertThat(contract.ticketPrice()).isEqualByComparingTo("5");
        assertThat(contract.payoutRatio()).isEqualByComparingTo("0.65");
        assertThat(contract.minPayout()).isEqualByComparingTo("0");
        assertThat(contract.bookProfile()).isEqualTo(BookProfile.BALANCED);
        assertThat(contract.prizeTiers()).hasSize(3);
    }

    @Test
    void shouldComputeTotalRevenueAsTicketsTimesPrice() {
        PoolContract contract = validBuilder().build();

        assertThat(contract.getTotalRevenue()).isEqualByComparingTo("500");
    }

    @Test
    void shouldComputePrizeBudgetAsRevenueTimesPayoutRatio() {
        PoolContract contract = validBuilder().build();

        assertThat(contract.getPrizeBudget()).isEqualByComparingTo("325.00");
    }

    @Test
    void shouldComputeWinnerCountAsSumOfTierCounts() {
        PoolContract contract = validBuilder().build();

        assertThat(contract.getWinnerCount()).isEqualTo(26);
    }

    @Test
    void shouldComputeLoserCountAsTotalMinusWinners() {
        PoolContract contract = validBuilder().build();

        assertThat(contract.getLoserCount()).isEqualTo(74);
    }

    @Test
    void shouldComputeTierCostAsSumOfTierCosts() {
        PoolContract contract = validBuilder().build();

        // 100*1 + 10*5 + 2*20 = 100 + 50 + 40 = 190
        assertThat(contract.getTierCost()).isEqualByComparingTo("190");
    }

    @Test
    void shouldComputeFloorCostAsZeroWhenNoFloor() {
        PoolContract contract = validBuilder().build();

        assertThat(contract.getFloorCost()).isEqualByComparingTo("0");
    }

    @Test
    void shouldComputeFloorCostAsLoserCountTimesMinPayout() {
        PoolContract contract = validBuilder().minPayout(new BigDecimal("1")).build();

        // 74 losers * 1 = 74
        assertThat(contract.getFloorCost()).isEqualByComparingTo("74");
    }

    @Test
    void shouldComputeWinFrequencyAsWinnersOverTotal() {
        PoolContract contract = validBuilder().build();

        assertThat(contract.getWinFrequency()).isCloseTo(0.26, within(1e-9));
    }

    @Test
    void shouldSortPrizeTiersDescendingByValueRegardlessOfInsertionOrder() {
        PoolContract contract = PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.65"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 20, "Small"))
                .addPrizeTier(new PrizeTier(new BigDecimal("100"), 1, "Jackpot"))
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 5, "Mid"))
                .bookProfile(BookProfile.BALANCED)
                .build();

        assertThat(contract.prizeTiers())
                .extracting(PrizeTier::label)
                .containsExactly("Jackpot", "Mid", "Small");
    }

    @Test
    void shouldDefaultMinPayoutToZeroWhenNotSet() {
        PoolContract contract = PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.65"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 20, "Small"))
                .bookProfile(BookProfile.BALANCED)
                .build();

        assertThat(contract.minPayout()).isEqualByComparingTo("0");
    }

    @Test
    void shouldExposePrizeTiersAsUnmodifiableList() {
        PoolContract contract = validBuilder().build();

        assertThatThrownBy(() -> contract.prizeTiers().add(new PrizeTier(BigDecimal.ONE, 1, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldDefensivelyCopyPrizeTiersFromBuilder() {
        PoolContract.Builder builder = validBuilder();
        PoolContract contract = builder.build();

        // mutating the builder after build must not affect the built contract
        builder.addPrizeTier(new PrizeTier(new BigDecimal("500"), 1, "Late"));

        assertThat(contract.prizeTiers()).hasSize(3);
    }

    @Test
    void shouldAcceptBulkPrizeTiersList() {
        List<PrizeTier> tiers = List.of(
                new PrizeTier(new BigDecimal("2"), 20, "Small"),
                new PrizeTier(new BigDecimal("100"), 1, "Jackpot"));

        PoolContract contract = PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.65"))
                .prizeTiers(tiers)
                .bookProfile(BookProfile.BALANCED)
                .build();

        assertThat(contract.prizeTiers())
                .extracting(PrizeTier::label)
                .containsExactly("Jackpot", "Small");
    }

    @Test
    void shouldRejectNullTicketPrice() {
        assertThatThrownBy(() -> validBuilder().ticketPrice(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ticketPrice");
    }

    @Test
    void shouldRejectNullPayoutRatio() {
        assertThatThrownBy(() -> validBuilder().payoutRatio(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payoutRatio");
    }

    @Test
    void shouldRejectNullMinPayout() {
        assertThatThrownBy(() -> validBuilder().minPayout(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("minPayout");
    }

    @Test
    void shouldRejectNullBookProfile() {
        assertThatThrownBy(() -> validBuilder().bookProfile(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bookProfile");
    }

    @Test
    void shouldRejectNullPrizeTiersList() {
        assertThatThrownBy(() -> validBuilder().prizeTiers(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("prizeTiers");
    }

    @Test
    void shouldRejectNullElementInPrizeTiers() {
        assertThatThrownBy(() -> PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.65"))
                .addPrizeTier(null)
                .bookProfile(BookProfile.BALANCED)
                .build())
                .isInstanceOf(NullPointerException.class);
    }
}
