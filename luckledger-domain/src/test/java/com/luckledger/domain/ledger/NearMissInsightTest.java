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

class NearMissInsightTest {

    private static final Instant NOW = Instant.parse("2026-05-28T12:00:00Z");

    private final NearMissInsight insight = new NearMissInsight(Clock.fixed(NOW, ZoneOffset.UTC));

    private static LedgerSnapshot snapshotWith(int revealedLoserCount, int nearMissCount) {
        return new LedgerSnapshot(
                new BigDecimal("1000.00"),
                new BigDecimal("800.00"),
                new BigDecimal("300.00"),
                new BigDecimal("-500.00"),
                40,
                new BigDecimal("0.3750"),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                revealedLoserCount,
                nearMissCount);
    }

    @Test
    void shouldImplementInsightGenerator() {
        assertThat(insight).isInstanceOf(InsightGenerator.class);
    }

    @Test
    void shouldReturnInsight_whenNearMissCountAtThreshold() {
        Optional<Insight> result = insight.evaluate(snapshotWith(10, 5));

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo("NEAR_MISS");
        assertThat(result.get().severity()).isEqualTo(InsightSeverity.WARNING);
        assertThat(result.get().timestamp()).isEqualTo(NOW);
        assertThat(result.get().title()).isNotBlank();
    }

    @Test
    void shouldReturnEmpty_whenNearMissCountBelowThreshold() {
        assertThat(insight.evaluate(snapshotWith(20, 4))).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenNoNearMissesAndNoLosers() {
        assertThat(insight.evaluate(snapshotWith(0, 0))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void shouldNotTrigger_belowFiveNearMisses(int nearMissCount) {
        assertThat(insight.evaluate(snapshotWith(50, nearMissCount))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 10, 25})
    void shouldTrigger_atOrAboveFiveNearMisses(int nearMissCount) {
        assertThat(insight.evaluate(snapshotWith(50, nearMissCount))).isPresent();
    }

    @Test
    void shouldReportPercentageOfLosers_inMessage() {
        // 5 of 10 losers were near-misses -> 50%
        Insight result = insight.evaluate(snapshotWith(10, 5)).orElseThrow();

        assertThat(result.message()).contains("50%");
    }

    @Test
    void shouldRoundPercentageHalfUp_inMessage() {
        // 5 of 15 losers -> 33.33% -> "33%"
        Insight result = insight.evaluate(snapshotWith(15, 5)).orElseThrow();

        assertThat(result.message()).contains("33%");
    }

    @Test
    void shouldFrameNearMissAsEngineered_notLuck() {
        Insight result = insight.evaluate(snapshotWith(10, 6)).orElseThrow();

        assertThat(result.message()).containsIgnoringCase("engineered");
        assertThat(result.message()).containsIgnoringCase("not bad luck");
    }

    @Test
    void shouldExposeSupportingData_forFrontendRendering() {
        Insight result = insight.evaluate(snapshotWith(20, 7)).orElseThrow();

        assertThat(result.data())
                .containsEntry("nearMissCount", 7)
                .containsEntry("revealedLoserCount", 20)
                .containsKey("nearMissRate");
        assertThat((BigDecimal) result.data().get("nearMissRate")).isEqualByComparingTo("0.3500");
    }

    @Test
    void shouldRejectNullSnapshot() {
        assertThatNullPointerException().isThrownBy(() -> insight.evaluate(null));
    }

    @Test
    void shouldRejectNullClock() {
        assertThatNullPointerException().isThrownBy(() -> new NearMissInsight(null));
    }
}
