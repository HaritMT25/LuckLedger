package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

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

class InevitabilityCurveInsightTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-28T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private final InevitabilityCurveInsight generator = new InevitabilityCurveInsight(FIXED_CLOCK);

    @Test
    void shouldGenerateInsight_whenTicketCountAtLeast25_andNetPositionNegative() {
        LedgerSnapshot snapshot = snapshot(25, new BigDecimal("-1600.00"));

        Optional<Insight> result = generator.evaluate(snapshot);

        assertThat(result).isPresent();
    }

    @Test
    void shouldClassifyAsWarning() {
        Insight insight = generator.evaluate(snapshot(30, new BigDecimal("-2000.00"))).orElseThrow();

        assertThat(insight.severity()).isEqualTo(InsightSeverity.WARNING);
    }

    @Test
    void shouldUseStableMachineType() {
        Insight insight = generator.evaluate(snapshot(30, new BigDecimal("-2000.00"))).orElseThrow();

        assertThat(insight.type()).isEqualTo("INEVITABILITY_CURVE");
    }

    @Test
    void shouldDescribeTheWideningGap_usingActualTicketCount() {
        Insight insight = generator.evaluate(snapshot(40, new BigDecimal("-3200.00"))).orElseThrow();

        assertThat(insight.title()).isNotBlank();
        assertThat(insight.message())
                .contains("Over 40 tickets")
                .contains("This gap widens over time. It always does.");
    }

    @Test
    void shouldExposeSupportingDataForRendering() {
        Insight insight = generator.evaluate(snapshot(30, new BigDecimal("-1600.00"))).orElseThrow();

        Map<String, Object> data = insight.data();
        assertThat(data).containsEntry("ticketCount", 30);
        assertThat((BigDecimal) data.get("netPosition")).isEqualByComparingTo("-1600.00");
        assertThat((BigDecimal) data.get("totalSpent")).isEqualByComparingTo("4800.00");
        assertThat((BigDecimal) data.get("totalWon")).isEqualByComparingTo("3200.00");
    }

    @Test
    void shouldStampTimestampFromInjectedClock() {
        Insight insight = generator.evaluate(snapshot(30, new BigDecimal("-100.00"))).orElseThrow();

        assertThat(insight.timestamp()).isEqualTo(FIXED_NOW);
    }

    @Test
    void shouldReturnEmpty_whenTicketCountBelowThreshold() {
        Optional<Insight> result = generator.evaluate(snapshot(24, new BigDecimal("-1600.00")));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenNetPositionIsZero() {
        Optional<Insight> result = generator.evaluate(snapshot(30, BigDecimal.ZERO));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenNetPositionIsPositive() {
        Optional<Insight> result = generator.evaluate(snapshot(30, new BigDecimal("250.00")));

        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {25, 26, 100})
    void shouldFire_atOrAboveThreshold_whenLosing(int ticketCount) {
        assertThat(generator.evaluate(snapshot(ticketCount, new BigDecimal("-10.00")))).isPresent();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 24})
    void shouldNotFire_belowThreshold_evenWhenLosing(int ticketCount) {
        assertThat(generator.evaluate(snapshot(ticketCount, new BigDecimal("-10.00")))).isEmpty();
    }

    @Test
    void shouldExposeDefaultConstructorForRegistration() {
        InevitabilityCurveInsight defaultGenerator = new InevitabilityCurveInsight();

        assertThat(defaultGenerator.evaluate(snapshot(25, new BigDecimal("-1.00")))).isPresent();
    }

    private static LedgerSnapshot snapshot(int ticketCount, BigDecimal netPosition) {
        return new LedgerSnapshot(
                new BigDecimal("5000.00"),
                new BigDecimal("4800.00"),
                new BigDecimal("3200.00"),
                netPosition,
                ticketCount,
                new BigDecimal("0.6667"),
                Map.of(),
                Map.of(),
                List.of(),
                List.of());
    }
}
