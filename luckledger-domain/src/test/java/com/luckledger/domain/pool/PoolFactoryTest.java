package com.luckledger.domain.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoolFactoryTest {

    private final PoolFactory factory = new PoolFactory(new PoolValidator());

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /**
     * Baseline well-formed pool: 100 tickets @ $2.00, 50% RTP → $100.00 prize budget.
     * Tiers pay exactly $100.00 ($10×5 + $5×10), no floor, no duplicates.
     */
    private static PoolContract.Builder validBuilder() {
        return PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(bd("2.00"))
                .payoutRatio(bd("0.50"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .addPrizeTier(new PrizeTier(bd("10.00"), 5, "Ten"))
                .addPrizeTier(new PrizeTier(bd("5.00"), 10, "Five"));
    }

    private static BigDecimal lowestTierCount(PoolContract pool, String value) {
        PrizeTier tier = pool.prizeTiers().stream()
                .filter(t -> t.value().compareTo(bd(value)) == 0)
                .findFirst()
                .orElseThrow();
        return BigDecimal.valueOf(tier.count());
    }

    // ---- create(Builder) -------------------------------------------------

    @Test
    void shouldReturnImmutableContract_whenBuilderProducesValidPool() {
        PoolContract pool = factory.create(validBuilder());

        assertThat(pool.totalTickets()).isEqualTo(100);
        assertThat(pool.getWinnerCount()).isEqualTo(15);
        assertThat(pool.getLoserCount()).isEqualTo(85);
        assertThat(pool.getTierCost()).isEqualByComparingTo(pool.getPrizeBudget());
    }

    @Test
    void shouldThrowInvalidPoolException_whenPoolFailsValidation() {
        // Budget $100.00 but tiers pay $105.00 → off by $5, exceeds tolerance.
        PoolContract.Builder bad = PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(bd("2.00"))
                .payoutRatio(bd("0.50"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .addPrizeTier(new PrizeTier(bd("21.00"), 5, "TooMuch"));

        assertThatThrownBy(() -> factory.create(bad))
                .isInstanceOf(InvalidPoolException.class)
                .satisfies(ex -> assertThat(((InvalidPoolException) ex).getErrors())
                        .anyMatch(e -> e.toLowerCase().contains("budget")));
    }

    @Test
    void shouldThrow_whenBuilderIsNull() {
        assertThatThrownBy(() -> factory.create(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrow_whenValidatorIsNull() {
        assertThatThrownBy(() -> new PoolFactory(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- createWithAutoBalance(...) --------------------------------------

    @Test
    void shouldComputeLowestTierCount_soBudgetBalancesExactly() {
        // 10,000 tickets @ $1.00, 65% RTP → $6,500.00 budget.
        // Manual high tiers cost $5,000 ($2000×1 + $100×10 + $10×200);
        // the lowest $3 tier must absorb the remaining $1,500 → 500 tickets.
        List<PrizeTier> tiers = List.of(
                new PrizeTier(bd("2000.00"), 1, "Jackpot"),
                new PrizeTier(bd("100.00"), 10, "Hundred"),
                new PrizeTier(bd("10.00"), 200, "Ten"),
                new PrizeTier(bd("3.00"), 1, "Three")); // count is a placeholder, recomputed

        PoolContract pool = factory.createWithAutoBalance(10_000, bd("1.00"), bd("0.65"), tiers);

        assertThat(lowestTierCount(pool, "3.00")).isEqualByComparingTo(bd("500"));
        assertThat(pool.getTierCost()).isEqualByComparingTo(pool.getPrizeBudget());
        assertThat(new PoolValidator().validate(pool).isValid()).isTrue();
    }

    @Test
    void shouldAutoBalance_withASingleTier() {
        // 100 tickets @ $2.00, 50% RTP → $100 budget; single $5 tier → 20 tickets.
        List<PrizeTier> tiers = List.of(new PrizeTier(bd("5.00"), 1, "Five"));

        PoolContract pool = factory.createWithAutoBalance(100, bd("2.00"), bd("0.50"), tiers);

        assertThat(lowestTierCount(pool, "5.00")).isEqualByComparingTo(bd("20"));
        assertThat(pool.getLoserCount()).isEqualTo(80);
    }

    @Test
    void shouldAdjustTheLowestValueTier_regardlessOfInputOrder() {
        // Lowest ($2) is listed first; it should still be the one recomputed.
        // Budget: 1000 @ $1.00, 50% → $500. Other tier $50×4 = $200; $2 absorbs $300 → 150.
        List<PrizeTier> tiers = List.of(
                new PrizeTier(bd("2.00"), 999, "Two"), // placeholder count, recomputed
                new PrizeTier(bd("50.00"), 4, "Fifty"));

        PoolContract pool = factory.createWithAutoBalance(1_000, bd("1.00"), bd("0.50"), tiers);

        assertThat(lowestTierCount(pool, "2.00")).isEqualByComparingTo(bd("150"));
        assertThat(lowestTierCount(pool, "50.00")).isEqualByComparingTo(bd("4"));
    }

    @Test
    void shouldDefaultToNoFloorAndBalancedProfile() {
        PoolContract pool = factory.createWithAutoBalance(
                100, bd("2.00"), bd("0.50"), List.of(new PrizeTier(bd("5.00"), 1, "Five")));

        assertThat(pool.minPayout()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pool.bookProfile()).isEqualTo(BookProfile.BALANCED);
    }

    @Test
    void shouldThrowInvalidPoolException_whenRemainingBudgetCannotFundLowestTier() {
        // Budget $100; a $90 tier already consumes most of it, leaving $10,
        // which cannot fund even one $50 ticket → no whole-ticket fit.
        List<PrizeTier> tiers = List.of(
                new PrizeTier(bd("90.00"), 1, "Big"),
                new PrizeTier(bd("50.00"), 1, "Mid"));

        assertThatThrownBy(() -> factory.createWithAutoBalance(100, bd("2.00"), bd("0.50"), tiers))
                .isInstanceOf(InvalidPoolException.class);
    }

    @Test
    void shouldThrowInvalidPoolException_whenAutoBalancedPoolStillFailsValidation() {
        // High tiers alone ($60×1) already exceed the $50 budget; the lowest tier
        // would need a negative count to compensate → balancing is impossible.
        List<PrizeTier> tiers = List.of(
                new PrizeTier(bd("60.00"), 1, "Over"),
                new PrizeTier(bd("1.00"), 1, "One"));

        assertThatThrownBy(() -> factory.createWithAutoBalance(100, bd("1.00"), bd("0.50"), tiers))
                .isInstanceOf(InvalidPoolException.class);
    }

    @Test
    void shouldThrow_whenPrizeTiersEmpty() {
        assertThatThrownBy(() -> factory.createWithAutoBalance(100, bd("2.00"), bd("0.50"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrow_whenAutoBalanceArgumentsAreNull() {
        List<PrizeTier> tiers = List.of(new PrizeTier(bd("5.00"), 1, "Five"));

        assertThatThrownBy(() -> factory.createWithAutoBalance(100, null, bd("0.50"), tiers))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> factory.createWithAutoBalance(100, bd("2.00"), null, tiers))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> factory.createWithAutoBalance(100, bd("2.00"), bd("0.50"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
