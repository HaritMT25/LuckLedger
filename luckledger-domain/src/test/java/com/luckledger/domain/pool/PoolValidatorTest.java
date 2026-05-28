package com.luckledger.domain.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PoolValidatorTest {

    private final PoolValidator validator = new PoolValidator();

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

    @Test
    void shouldPassValidation_whenPoolIsWellFormed() {
        ValidationResult result = validator.validate(validBuilder().build());

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldThrow_whenPoolIsNull() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldPass_whenBudgetMismatchIsWithinTolerance() {
        // Budget $100.0000; tiers pay $100.005 → off by half a cent (< $0.01 tolerance).
        PoolContract pool = PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(bd("2.00"))
                .payoutRatio(bd("0.50"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .addPrizeTier(new PrizeTier(bd("20.001"), 5, "OffByHalfCent"))
                .build();

        ValidationResult result = validator.validate(pool);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldReject_whenBudgetMismatchExceedsTolerance() {
        // Budget $100.00; tiers pay $105.00 → off by $5.
        PoolContract pool = PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(bd("2.00"))
                .payoutRatio(bd("0.50"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .addPrizeTier(new PrizeTier(bd("21.00"), 5, "TooMuch"))
                .build();

        ValidationResult result = validator.validate(pool);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.toLowerCase().contains("budget"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void shouldReject_whenTotalTicketsNotPositive(int totalTickets) {
        PoolContract pool = validBuilder().totalTickets(totalTickets).build();

        ValidationResult result = validator.validate(pool);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("totalTickets"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "1.50", "-0.10"})
    void shouldReject_whenPayoutRatioOutOfOpenUnitInterval(String ratio) {
        PoolContract pool = validBuilder().payoutRatio(bd(ratio)).build();

        ValidationResult result = validator.validate(pool);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("payoutRatio"));
    }

    @Test
    void shouldReject_whenWinningTierValueDoesNotExceedFloor() {
        // minPayout floor is $5.00 but a tier pays only $3.00.
        PoolContract pool = validBuilder()
                .minPayout(bd("5.00"))
                .prizeTiers(java.util.List.of(new PrizeTier(bd("3.00"), 10, "BelowFloor")))
                .build();

        ValidationResult result = validator.validate(pool);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.toLowerCase().contains("minimum payout"));
    }

    @Test
    void shouldReject_whenTierValuesAreDuplicated() {
        // 10.00 and 10.000 are numerically equal (compareTo == 0) → ambiguous prize amount.
        // Budget still balances ($50 + $50 == $100) so duplication is the sole defect.
        PoolContract pool = PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(bd("2.00"))
                .payoutRatio(bd("0.50"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .addPrizeTier(new PrizeTier(bd("10.00"), 5, "A"))
                .addPrizeTier(new PrizeTier(bd("10.000"), 5, "B"))
                .build();

        ValidationResult result = validator.validate(pool);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.toLowerCase().contains("duplicate"));
    }

    @Test
    void shouldReject_whenWinnerCountExceedsTotalTickets() {
        // 20 winners but only 10 tickets → loserCount == -10.
        PoolContract pool = PoolContract.builder()
                .totalTickets(10)
                .ticketPrice(bd("2.00"))
                .payoutRatio(bd("0.50"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .addPrizeTier(new PrizeTier(bd("1.00"), 20, "Overflow"))
                .build();

        ValidationResult result = validator.validate(pool);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.toLowerCase().contains("loser"));
    }

    @Test
    void shouldAccumulateMultipleErrors_whenSeveralChecksFail() {
        // totalTickets == 0 trips totalTickets, loserCount and budget checks at once.
        PoolContract pool = validBuilder().totalTickets(0).build();

        ValidationResult result = validator.validate(pool);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors().size()).isGreaterThanOrEqualTo(2);
    }
}
