package com.luckledger.domain.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Educational observation that surfaces the deliberate engineering of near-misses.
 *
 * <p>Real lotteries engineer loser tickets that "almost win" (e.g. 2-of-3 matching symbols) to make
 * losers feel close to a win and drive repeat play (DESIGN §3.11). This generator counts how many of
 * the player's revealed losing tickets were such near-misses and, once that count is significant,
 * frames the pattern as an engineered manipulation rather than bad luck.
 *
 * <p>Triggers once at least {@value #MIN_NEAR_MISSES} revealed losing tickets were near-misses. The
 * reported rate is {@code nearMissCount / revealedLoserCount}; because a near-miss is itself a loser,
 * {@code revealedLoserCount} is always positive whenever the trigger fires.
 *
 * <p>Severity is {@link InsightSeverity#WARNING}: an engineered manipulation worth the player's
 * attention. The generator is stateless; its only collaborator is a {@link Clock} so the produced
 * insight's timestamp is deterministic and testable.
 */
public final class NearMissInsight implements InsightGenerator {

    /** Minimum number of near-miss losing tickets for the insight to fire. */
    static final int MIN_NEAR_MISSES = 5;

    private static final String TYPE = "NEAR_MISS";
    private static final int RATE_SCALE = 4;

    private final Clock clock;

    /**
     * Creates a generator that stamps produced insights using the supplied clock.
     *
     * @param clock source of the insight timestamp; never {@code null}
     */
    public NearMissInsight(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Returns a near-miss insight when the player has accumulated at least
     * {@value #MIN_NEAR_MISSES} near-miss losing tickets.
     *
     * @param snapshot the player's ledger state; never {@code null}
     * @return the near-miss insight, or {@link Optional#empty()} if the threshold is not met
     * @throws NullPointerException if {@code snapshot} is {@code null}
     */
    @Override
    public Optional<Insight> evaluate(LedgerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        int nearMissCount = snapshot.nearMissCount();
        int revealedLoserCount = snapshot.revealedLoserCount();
        if (nearMissCount < MIN_NEAR_MISSES) {
            return Optional.empty();
        }

        BigDecimal rate = BigDecimal.valueOf(nearMissCount)
                .divide(BigDecimal.valueOf(revealedLoserCount), RATE_SCALE, RoundingMode.HALF_UP);
        int percent = rate.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();

        String message = percent + "% of your losing tickets had 2-of-3 matching symbols. "
                + "This is an engineered manipulation, not bad luck. "
                + "Real lotteries design near-misses to make you feel like you almost won.";

        Insight insight = new Insight(
                TYPE,
                InsightSeverity.WARNING,
                "Your near-misses were engineered",
                message,
                Map.of(
                        "nearMissCount", nearMissCount,
                        "revealedLoserCount", revealedLoserCount,
                        "nearMissRate", rate),
                Instant.now(clock));
        return Optional.of(insight);
    }
}
