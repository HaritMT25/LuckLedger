package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LossRateInsightTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-05-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(TIMESTAMP, ZoneOffset.UTC);

    private final LossRateInsight generator = new LossRateInsight(CLOCK);

    @Test
    void shouldRejectNullClock() {
        assertThatNullPointerException().isThrownBy(() -> new LossRateInsight(null));
    }

    @Test
    void shouldGenerateInsight_whenReturnRateBelowThresholdAndEnoughTickets() {
        Optional<Insight> result = generator.evaluate(snapshot("0.6667", 10, "4800", "3200"));

        assertThat(result).isPresent();
        Insight insight = result.get();
        assertThat(insight.type()).isEqualTo("LOSS_RATE");
        assertThat(insight.severity()).isEqualTo(InsightSeverity.WARNING);
        assertThat(insight.title()).isEqualTo("Your loss rate");
        assertThat(insight.timestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    void shouldMatchDesignExampleMessage() {
        Insight insight = generator.evaluate(snapshot("0.6667", 12, "4800", "3200")).orElseThrow();

        assertThat(insight.message())
                .contains("4,800")
                .contains("3,200")
                .contains("33%");
    }

    @Test
    void shouldExposeSupportingData() {
        Insight insight = generator.evaluate(snapshot("0.6667", 12, "4800", "3200")).orElseThrow();

        assertThat(insight.data())
                .containsEntry("totalSpent", new BigDecimal("4800"))
                .containsEntry("totalWon", new BigDecimal("3200"))
                .containsEntry("ticketCount", 12)
                .containsKey("lossRate");
    }

    @Test
    void shouldReturnEmpty_whenReturnRateExactlyAtThreshold() {
        assertThat(generator.evaluate(snapshot("0.70", 50, "1000", "700"))).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenReturnRateAboveThreshold() {
        assertThat(generator.evaluate(snapshot("0.95", 50, "1000", "950"))).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenFewerThanTenTickets() {
        assertThat(generator.evaluate(snapshot("0.10", 9, "1000", "100"))).isEmpty();
    }

    @Test
    void shouldFire_atExactlyTenTickets() {
        assertThat(generator.evaluate(snapshot("0.10", 10, "1000", "100"))).isPresent();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 9})
    void shouldReturnEmpty_belowTicketThreshold(int ticketCount) {
        assertThat(generator.evaluate(snapshot("0.10", ticketCount, "1000", "100"))).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenNothingSpent() {
        assertThat(generator.evaluate(snapshot("0.00", 10, "0", "0"))).isEmpty();
    }

    private LedgerSnapshot snapshot(String returnRate, int ticketCount, String spent, String won) {
        BigDecimal spentBd = new BigDecimal(spent);
        BigDecimal wonBd = new BigDecimal(won);
        return new LedgerSnapshot(
                BigDecimal.ZERO,
                spentBd,
                wonBd,
                wonBd.subtract(spentBd),
                ticketCount,
                new BigDecimal(returnRate),
                Map.of(),
                Map.of(),
                List.of(),
                List.of());
    }
}
