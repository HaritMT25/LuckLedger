package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.GenerationResult;
import com.luckledger.domain.generation.verification.CheckResult;
import com.luckledger.domain.generation.verification.NearMissReport;
import com.luckledger.domain.generation.verification.VerificationReport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameSetupResultTest {

    private static GenerationResult generation() {
        return new GenerationResult(
                List.of(),
                VerificationReport.from(List.of(CheckResult.passed("Tier Counts"))),
                new NearMissReport(0, 0, 0.0, Map.of()),
                5L);
    }

    private static Dealer dealer() {
        return new Dealer(UUID.randomUUID(), "d", DealerTier.TIER_1, 0, 3, 0);
    }

    private static PartitionResult partition() {
        return new PartitionResult(List.of(Cards.book(10)), new BookValueStats(10, 10, 10, 0, 10));
    }

    @Test
    void holdsItsComponents() {
        Dealer d = dealer();
        TicketBook book = Cards.book(10);
        GameSetupResult result = new GameSetupResult(
                List.of(d), generation(), partition(), Map.of(d, List.of(book)));

        assertThat(result.dealers()).containsExactly(d);
        assertThat(result.generationResult()).isNotNull();
        assertThat(result.partitionResult()).isNotNull();
        assertThat(result.allocationMap()).containsKey(d);
    }

    @Test
    void dealersAndAllocationMapAreUnmodifiable() {
        Dealer d = dealer();
        GameSetupResult result = new GameSetupResult(
                List.of(d), generation(), partition(), Map.of(d, List.of(Cards.book(1))));

        assertThatThrownBy(() -> result.dealers().add(dealer()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.allocationMap().put(dealer(), List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullsAreRejected() {
        Dealer d = dealer();
        assertThatThrownBy(() -> new GameSetupResult(null, generation(), partition(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GameSetupResult(List.of(d), null, partition(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GameSetupResult(List.of(d), generation(), null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GameSetupResult(List.of(d), generation(), partition(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
