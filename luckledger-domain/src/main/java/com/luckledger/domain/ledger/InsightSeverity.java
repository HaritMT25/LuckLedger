package com.luckledger.domain.ledger;

/**
 * Severity classification for an educational insight surfaced from the ledger.
 *
 * <ul>
 *   <li>{@link #INFO} — neutral, informational observation.</li>
 *   <li>{@link #WARNING} — a pattern worth the player's attention.</li>
 *   <li>{@link #CRITICAL} — a strong signal of harmful play (e.g. loss chasing).</li>
 * </ul>
 */
public enum InsightSeverity {
    INFO,
    WARNING,
    CRITICAL
}
