package com.luckledger.domain.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Educational insight that surfaces how much of a player's spending has not come back as winnings.
 *
 * <p>Trigger: the player's rolling return rate has dropped below {@value #RETURN_RATE_THRESHOLD_STR}
 * (i.e. they have won back less than 70% of what they spent) and they have bought at least
 * {@value #MIN_TICKETS} tickets — enough plays that the figure reflects the game's economics rather
 * than the noise of a handful of tickets. The insight reports the loss rate plainly so the player
 * sees the house edge accumulating (see DESIGN §11).
 *
 * <p>Severity is {@link InsightSeverity#WARNING}: a loss rate worth the player's attention, short of
 * the {@link InsightSeverity#CRITICAL} signals reserved for harmful behaviour such as loss chasing.
 * The generator is stateless; its only collaborator is a {@link Clock} so the produced insight's
 * timestamp is deterministic and testable.
 */
public final class LossRateInsight implements InsightGenerator {

    /** Minimum tickets bought before the loss rate is considered representative. */
    static final int MIN_TICKETS = 10;

    static final String RETURN_RATE_THRESHOLD_STR = "0.70";

    /** Rolling return rate at or above which the insight does not fire. */
    static final BigDecimal RETURN_RATE_THRESHOLD = new BigDecimal(RETURN_RATE_THRESHOLD_STR);

    private static final String TYPE = "LOSS_RATE";
    private static final String TITLE = "Your loss rate";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Clock clock;

    /**
     * Creates a generator that stamps produced insights using the supplied clock.
     *
     * @param clock source of the insight timestamp; never {@code null}
     */
    public LossRateInsight(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Produces a loss-rate insight when the player's rolling return rate is below
     * {@value #RETURN_RATE_THRESHOLD_STR} and they have bought at least {@value #MIN_TICKETS}
     * tickets. The loss rate shown in the message and supporting data is derived from the
     * snapshot's spent and won totals.
     *
     * @param snapshot read-only aggregate of the player's ledger state; never {@code null}
     * @return the insight when the trigger condition holds and spending is positive, otherwise
     *         {@link Optional#empty()}
     */
    @Override
    public Optional<Insight> evaluate(LedgerSnapshot snapshot) {
        boolean belowThreshold = snapshot.rollingReturnRate().compareTo(RETURN_RATE_THRESHOLD) < 0;
        boolean enoughTickets = snapshot.ticketCount() >= MIN_TICKETS;
        boolean hasSpent = snapshot.totalSpent().compareTo(BigDecimal.ZERO) > 0;
        if (!belowThreshold || !enoughTickets || !hasSpent) {
            return Optional.empty();
        }

        BigDecimal totalSpent = snapshot.totalSpent();
        BigDecimal totalWon = snapshot.totalWon();
        BigDecimal lossRate = totalSpent.subtract(totalWon)
                .divide(totalSpent, 4, RoundingMode.HALF_UP);

        Map<String, Object> data = Map.of(
                "totalSpent", totalSpent,
                "totalWon", totalWon,
                "ticketCount", snapshot.ticketCount(),
                "rollingReturnRate", snapshot.rollingReturnRate(),
                "lossRate", lossRate);

        String message = "You've spent %s coins and won back %s. That's a %s loss rate."
                .formatted(formatCoins(totalSpent), formatCoins(totalWon), percent(lossRate));

        return Optional.of(new Insight(TYPE, InsightSeverity.WARNING, TITLE, message, data, clock.instant()));
    }

    private static String percent(BigDecimal fraction) {
        return fraction.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String formatCoins(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0", new DecimalFormatSymbols(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount);
    }
}
