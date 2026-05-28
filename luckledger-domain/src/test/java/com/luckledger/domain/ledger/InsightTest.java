package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InsightTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-05-28T12:00:00Z");

    @Test
    void shouldConstructValidInsight_andExposeAccessors() {
        Map<String, Object> data = Map.of("totalSpent", 4800, "totalWon", 3200, "lossRate", 0.33);

        Insight insight = new Insight(
                "LOSS_RATE",
                InsightSeverity.WARNING,
                "You are losing money",
                "You've spent 4,800 coins and won back 3,200. That's a 33% loss rate.",
                data,
                TIMESTAMP);

        assertThat(insight.type()).isEqualTo("LOSS_RATE");
        assertThat(insight.severity()).isEqualTo(InsightSeverity.WARNING);
        assertThat(insight.title()).isEqualTo("You are losing money");
        assertThat(insight.message())
                .isEqualTo("You've spent 4,800 coins and won back 3,200. That's a 33% loss rate.");
        assertThat(insight.data())
                .containsEntry("totalSpent", 4800)
                .containsEntry("totalWon", 3200)
                .containsEntry("lossRate", 0.33);
        assertThat(insight.timestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    void shouldAcceptEmptyData() {
        Insight insight = new Insight(
                "INFO_NOTE",
                InsightSeverity.INFO,
                "Heads up",
                "An informational observation.",
                Map.of(),
                TIMESTAMP);

        assertThat(insight.data()).isEmpty();
    }

    @Test
    void shouldDefensivelyCopyData_soSourceMutationDoesNotLeak() {
        Map<String, Object> source = new HashMap<>();
        source.put("count", 1);

        Insight insight = new Insight(
                "LOSS_CHASING",
                InsightSeverity.CRITICAL,
                "Loss chasing",
                "You've borrowed coins 3 times in the last 10 transactions.",
                source,
                TIMESTAMP);

        source.put("count", 999);
        source.put("injected", "later");

        assertThat(insight.data()).containsExactly(Map.entry("count", 1));
    }

    @Test
    void shouldExposeUnmodifiableData() {
        Insight insight = new Insight(
                "LOSS_RATE",
                InsightSeverity.WARNING,
                "Title",
                "Message",
                new HashMap<>(Map.of("k", 1)),
                TIMESTAMP);

        assertThatThrownBy(() -> insight.data().put("x", 2))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> new Insight(
                null, InsightSeverity.INFO, "Title", "Message", Map.of(), TIMESTAMP))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
    }

    @Test
    void shouldRejectBlankType() {
        assertThatThrownBy(() -> new Insight(
                "  ", InsightSeverity.INFO, "Title", "Message", Map.of(), TIMESTAMP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void shouldRejectNullSeverity() {
        assertThatThrownBy(() -> new Insight(
                "TYPE", null, "Title", "Message", Map.of(), TIMESTAMP))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("severity");
    }

    @Test
    void shouldRejectNullTitle() {
        assertThatThrownBy(() -> new Insight(
                "TYPE", InsightSeverity.INFO, null, "Message", Map.of(), TIMESTAMP))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("title");
    }

    @Test
    void shouldRejectBlankTitle() {
        assertThatThrownBy(() -> new Insight(
                "TYPE", InsightSeverity.INFO, " ", "Message", Map.of(), TIMESTAMP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void shouldRejectNullMessage() {
        assertThatThrownBy(() -> new Insight(
                "TYPE", InsightSeverity.INFO, "Title", null, Map.of(), TIMESTAMP))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("message");
    }

    @Test
    void shouldRejectBlankMessage() {
        assertThatThrownBy(() -> new Insight(
                "TYPE", InsightSeverity.INFO, "Title", "", Map.of(), TIMESTAMP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }

    @Test
    void shouldRejectNullData() {
        assertThatThrownBy(() -> new Insight(
                "TYPE", InsightSeverity.INFO, "Title", "Message", null, TIMESTAMP))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("data");
    }

    @Test
    void shouldRejectNullTimestamp() {
        assertThatThrownBy(() -> new Insight(
                "TYPE", InsightSeverity.INFO, "Title", "Message", Map.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timestamp");
    }
}
