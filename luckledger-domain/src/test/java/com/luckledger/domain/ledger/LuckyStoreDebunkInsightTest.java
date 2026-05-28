package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LuckyStoreDebunkInsightTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-05-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(TIMESTAMP, ZoneOffset.UTC);

    private final LuckyStoreDebunkInsight generator = new LuckyStoreDebunkInsight(CLOCK);

    @Test
    void shouldReturnEmpty_whenFewerThanTwentyTickets() {
        Map<UUID, DealerStats> dealers = new LinkedHashMap<>();
        putDealer(dealers, "Joe's", "0.72");
        putDealer(dealers, "Downtown", "0.55");

        assertThat(generator.evaluate(snapshot(19, dealers))).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenSpreadAtThreshold() {
        Map<UUID, DealerStats> dealers = new LinkedHashMap<>();
        putDealer(dealers, "Joe's", "0.70");
        putDealer(dealers, "Downtown", "0.55"); // spread exactly 0.15 — not strictly greater

        assertThat(generator.evaluate(snapshot(25, dealers))).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenSpreadBelowThreshold() {
        Map<UUID, DealerStats> dealers = new LinkedHashMap<>();
        putDealer(dealers, "Joe's", "0.72");
        putDealer(dealers, "Downtown", "0.58"); // spread 0.14

        assertThat(generator.evaluate(snapshot(25, dealers))).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenOnlyOneDealer() {
        Map<UUID, DealerStats> dealers = new LinkedHashMap<>();
        putDealer(dealers, "Joe's", "0.40");

        assertThat(generator.evaluate(snapshot(30, dealers))).isEmpty();
    }

    @Test
    void shouldIgnoreDealersWithNoSpend_whenComputingSpread() {
        Map<UUID, DealerStats> dealers = new LinkedHashMap<>();
        putDealer(dealers, "Joe's", "0.72");
        // A dealer present in the snapshot but never actually bought from.
        dealers.put(UUID.randomUUID(),
                new DealerStats(UUID.randomUUID(), "Untouched", 0,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(generator.evaluate(snapshot(25, dealers))).isEmpty();
    }

    @Test
    void shouldGenerateInsight_whenSpreadExceedsThresholdAndEnoughTickets() {
        Map<UUID, DealerStats> dealers = new LinkedHashMap<>();
        putDealer(dealers, "Joe's", "0.72");
        putDealer(dealers, "Downtown", "0.55"); // spread 0.17 > 0.15

        Optional<Insight> result = generator.evaluate(snapshot(20, dealers));

        assertThat(result).isPresent();
        Insight insight = result.get();
        assertThat(insight.type()).isEqualTo("LUCKY_STORE_DEBUNK");
        assertThat(insight.severity()).isEqualTo(InsightSeverity.INFO);
        assertThat(insight.title()).isNotBlank();
        assertThat(insight.timestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    void shouldContrastHighestAndLowestDealers_inMessageAndData() {
        Map<UUID, DealerStats> dealers = new LinkedHashMap<>();
        UUID highId = putDealer(dealers, "Joe's", "0.72");
        putDealer(dealers, "Midtown", "0.63");
        UUID lowId = putDealer(dealers, "Downtown", "0.55");

        Insight insight = generator.evaluate(snapshot(24, dealers)).orElseThrow();

        assertThat(insight.message())
                .contains("Joe's").contains("72%")
                .contains("Downtown").contains("55%")
                .contains("variance, not the store");
        assertThat(insight.data())
                .containsEntry("dealerCount", 3)
                .containsEntry("ticketCount", 24)
                .containsEntry("highestReturnDealerId", highId)
                .containsEntry("highestReturnDealerName", "Joe's")
                .containsEntry("highestReturnRate", new BigDecimal("0.72"))
                .containsEntry("lowestReturnDealerId", lowId)
                .containsEntry("lowestReturnDealerName", "Downtown")
                .containsEntry("lowestReturnRate", new BigDecimal("0.55"))
                .containsEntry("returnRateSpread", new BigDecimal("0.17"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 19})
    void shouldReturnEmpty_belowTicketThreshold_evenWithLargeSpread(int ticketCount) {
        Map<UUID, DealerStats> dealers = new LinkedHashMap<>();
        putDealer(dealers, "Joe's", "0.90");
        putDealer(dealers, "Downtown", "0.30");

        assertThat(generator.evaluate(snapshot(ticketCount, dealers))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "0.72, 0.55, true",   // spread 0.17 -> fires
            "0.70, 0.55, false",  // spread 0.15 -> at threshold, silent
            "0.71, 0.55, true",   // spread 0.16 -> just over, fires
            "0.60, 0.55, false"   // spread 0.05 -> silent
    })
    void shouldFireOnlyWhenSpreadStrictlyExceedsFifteenPoints(
            String highRate, String lowRate, boolean shouldFire) {
        Map<UUID, DealerStats> dealers = new LinkedHashMap<>();
        putDealer(dealers, "Joe's", highRate);
        putDealer(dealers, "Downtown", lowRate);

        assertThat(generator.evaluate(snapshot(20, dealers)).isPresent()).isEqualTo(shouldFire);
    }

    private static UUID putDealer(Map<UUID, DealerStats> dealers, String name, String returnRate) {
        UUID dealerId = UUID.randomUUID();
        BigDecimal spent = new BigDecimal("100.00");
        dealers.put(dealerId, new DealerStats(
                dealerId,
                name,
                10,
                spent,
                spent.multiply(new BigDecimal(returnRate)),
                new BigDecimal(returnRate)));
        return dealerId;
    }

    private static LedgerSnapshot snapshot(int ticketCount, Map<UUID, DealerStats> perDealerStats) {
        return new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ticketCount,
                BigDecimal.ZERO,
                perDealerStats,
                Map.of(),
                List.of(),
                List.of());
    }
}
