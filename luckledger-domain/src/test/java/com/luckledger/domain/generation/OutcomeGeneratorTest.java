package com.luckledger.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.pool.BookProfile;
import com.luckledger.domain.pool.PoolContract;
import com.luckledger.domain.pool.PrizeTier;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutcomeGeneratorTest {

    private final OutcomeGenerator generator = new OutcomeGenerator();

    private static PoolContract pool(BigDecimal minPayout) {
        return PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.65"))
                .addPrizeTier(new PrizeTier(new BigDecimal("100"), 1, "Top"))
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 9, "Mid"))
                .minPayout(minPayout)
                .bookProfile(BookProfile.BALANCED)
                .build();
    }

    @Test
    void producesExactlyTotalTicketsOutcomes() {
        List<TicketOutcome> outcomes = generator.generate(pool(BigDecimal.ZERO));

        assertThat(outcomes).hasSize(100);
    }

    @Test
    void realizesEachTierCountExactly() {
        List<TicketOutcome> outcomes = generator.generate(pool(BigDecimal.ZERO));

        long top = outcomes.stream().filter(o -> o.prizeAmount().compareTo(new BigDecimal("100")) == 0).count();
        long mid = outcomes.stream().filter(o -> o.prizeAmount().compareTo(new BigDecimal("10")) == 0).count();
        long losers = outcomes.stream().filter(o -> o.prizeAmount().signum() == 0).count();

        assertThat(top).isEqualTo(1);
        assertThat(mid).isEqualTo(9);
        assertThat(losers).isEqualTo(90);
    }

    @Test
    void losersAreEmittedAtMinPayout() {
        List<TicketOutcome> outcomes = generator.generate(pool(new BigDecimal("1")));

        BigDecimal floor = new BigDecimal("1");
        long winners = outcomes.stream().filter(o -> o.isWinner(floor)).count();
        long atFloor = outcomes.stream().filter(o -> o.prizeAmount().compareTo(floor) == 0).count();

        assertThat(winners).isEqualTo(10); // only the $100 and $10 tiers, not the $1 floor
        assertThat(atFloor).isEqualTo(90);
    }

    @Test
    void isUnshuffledWinnersGroupedByTierDescending() {
        List<TicketOutcome> outcomes = generator.generate(pool(BigDecimal.ZERO));

        // first the single $100, then nine $10, then ninety $0 — grouped, value descending
        assertThat(outcomes.get(0).prizeAmount()).isEqualByComparingTo("100");
        assertThat(outcomes.get(1).prizeAmount()).isEqualByComparingTo("10");
        assertThat(outcomes.get(9).prizeAmount()).isEqualByComparingTo("10");
        assertThat(outcomes.get(10).prizeAmount()).isEqualByComparingTo("0");
        assertThat(outcomes.get(99).prizeAmount()).isEqualByComparingTo("0");
    }

    @Test
    void everyOutcomeHasADistinctId() {
        List<TicketOutcome> outcomes = generator.generate(pool(BigDecimal.ZERO));

        Set<UUID> ids = new HashSet<>();
        outcomes.forEach(o -> ids.add(o.outcomeId()));
        assertThat(ids).hasSize(100);
    }

    @Test
    void nullPoolIsRejected() {
        assertThatThrownBy(() -> generator.generate(null)).isInstanceOf(NullPointerException.class);
    }
}
