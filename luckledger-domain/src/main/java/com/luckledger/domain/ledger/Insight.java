package com.luckledger.domain.ledger;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable educational observation surfaced from a player's ledger.
 *
 * <p>An insight is produced by an {@code InsightGenerator} when a trigger condition is met
 * (e.g. a loss rate below target, loss-chasing behaviour). It carries a stable machine
 * {@code type} for frontend routing, a {@link InsightSeverity}, human-readable {@code title}
 * and {@code message}, and a {@code data} map of supporting numbers the frontend can use for
 * rendering. The {@code data} map is defensively copied into an unmodifiable map on
 * construction, so the value object is fully immutable and its keys and values must be non-null.
 *
 * @param type      stable machine identifier for the insight kind; never {@code null} or blank
 * @param severity  severity classification; never {@code null}
 * @param title     short human-readable headline; never {@code null} or blank
 * @param message   full human-readable explanation; never {@code null} or blank
 * @param data      supporting values keyed by name for frontend rendering; never {@code null},
 *                  copied into an unmodifiable map (no null keys or values)
 * @param timestamp when the insight was produced; never {@code null}
 */
public record Insight(
        String type,
        InsightSeverity severity,
        String title,
        String message,
        Map<String, Object> data,
        Instant timestamp) {

    /**
     * Validates the insight and defensively copies {@code data} on construction.
     *
     * @throws NullPointerException     if any field is {@code null}, or if {@code data} contains
     *                                  a {@code null} key or value
     * @throws IllegalArgumentException if {@code type}, {@code title}, or {@code message} is blank
     */
    public Insight {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        data = Map.copyOf(data);
    }
}
