package com.luckledger.domain.ledger;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Educational insight that surfaces the widening "inevitability curve": once a player has bought
 * enough tickets while sitting at a net loss, their cumulative winnings keep falling further behind
 * their cumulative spending. This is the central lesson of the simulator — a negative expected
 * value gap that only grows with more play.
 *
 * <p>Stateless and pure: each call derives its result solely from the supplied {@link LedgerSnapshot}.
 * Trigger condition: {@code ticketCount >= 25} and {@code netPosition < 0}.
 */
public final class InevitabilityCurveInsight implements InsightGenerator {

    static final String TYPE = "INEVITABILITY_CURVE";
    private static final int TICKET_THRESHOLD = 25;

    private final Clock clock;

    /** Creates a generator that stamps insights using the system UTC clock. */
    public InevitabilityCurveInsight() {
        this(Clock.systemUTC());
    }

    /**
     * Creates a generator that stamps insights using the supplied clock.
     *
     * @param clock clock used for the insight timestamp; never {@code null}
     */
    public InevitabilityCurveInsight(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Produces an inevitability-curve insight when the player has bought at least 25 tickets and is
     * currently at a net loss.
     *
     * @param snapshot read-only aggregate of the player's ledger state; never {@code null}
     * @return the insight when the trigger fires, otherwise {@link Optional#empty()}
     */
    @Override
    public Optional<Insight> evaluate(LedgerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (snapshot.ticketCount() < TICKET_THRESHOLD
                || snapshot.netPosition().compareTo(BigDecimal.ZERO) >= 0) {
            return Optional.empty();
        }

        String message = "Over " + snapshot.ticketCount()
                + " tickets, your cumulative winnings have fallen further behind your cumulative"
                + " spending. This gap widens over time. It always does.";
        Map<String, Object> data = Map.of(
                "ticketCount", snapshot.ticketCount(),
                "netPosition", snapshot.netPosition(),
                "totalSpent", snapshot.totalSpent(),
                "totalWon", snapshot.totalWon());

        return Optional.of(new Insight(
                TYPE,
                InsightSeverity.WARNING,
                "The gap keeps widening",
                message,
                data,
                clock.instant()));
    }
}
