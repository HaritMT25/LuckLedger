package com.luckledger.domain.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Educational insight that debunks the "lucky store" gambler's fallacy.
 *
 * <p>Players tend to attribute a good run at one dealer (or a bad run at another) to the store
 * itself rather than to chance. This insight fires once a player has played enough to have a
 * meaningful sample and their per-dealer return rates have diverged noticeably, then points out
 * that the books all carried the same payout ratio — the gap is variance, not the store.
 *
 * <p>Trigger: the player has bought at least {@value #MIN_TICKETS} tickets and the spread between
 * their highest- and lowest-returning dealers (return rates differing by more than
 * {@code 15} percentage points) exceeds {@link #SPREAD_THRESHOLD}. Only dealers the player
 * actually bought from (tickets bought and coins spent both positive) are considered, and at
 * least {@value #MIN_DEALERS} such dealers are required for a spread to exist.
 *
 * <p>Severity is {@link InsightSeverity#INFO}: this corrects a cognitive bias as a neutral
 * teaching moment, not a warning about harmful play. The generator is stateless; its only
 * collaborator is a {@link Clock} so the produced insight's timestamp is deterministic.
 */
public final class LuckyStoreDebunkInsight implements InsightGenerator {

    /** Minimum tickets bought before the sample is large enough for the insight to fire. */
    static final int MIN_TICKETS = 20;

    /** Minimum number of dealers a player must have bought from for a spread to exist. */
    static final int MIN_DEALERS = 2;

    /** Return-rate spread (15 percentage points) that must be strictly exceeded to fire. */
    static final BigDecimal SPREAD_THRESHOLD = new BigDecimal("0.15");

    private static final String TYPE = "LUCKY_STORE_DEBUNK";
    private static final String TITLE = "It's variance, not the store";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Clock clock;

    /**
     * Creates a generator that stamps produced insights using the supplied clock.
     *
     * @param clock source of the insight timestamp; never {@code null}
     */
    public LuckyStoreDebunkInsight(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Produces a lucky-store-debunk insight when the player has bought at least
     * {@value #MIN_TICKETS} tickets and their highest- and lowest-returning dealers differ by
     * more than 15 percentage points, contrasting the two dealers by name and return rate.
     *
     * @param snapshot read-only aggregate of the player's ledger state; never {@code null}
     * @return the insight when the trigger condition holds, otherwise {@link Optional#empty()}
     */
    @Override
    public Optional<Insight> evaluate(LedgerSnapshot snapshot) {
        if (snapshot.ticketCount() < MIN_TICKETS) {
            return Optional.empty();
        }

        List<DealerStats> boughtFrom = snapshot.perDealerStats().values().stream()
                .filter(LuckyStoreDebunkInsight::wasBoughtFrom)
                .sorted(Comparator.comparing(DealerStats::returnRate))
                .toList();

        if (boughtFrom.size() < MIN_DEALERS) {
            return Optional.empty();
        }

        DealerStats lowest = boughtFrom.get(0);
        DealerStats highest = boughtFrom.get(boughtFrom.size() - 1);
        BigDecimal spread = highest.returnRate().subtract(lowest.returnRate());

        if (spread.compareTo(SPREAD_THRESHOLD) <= 0) {
            return Optional.empty();
        }

        Map<String, Object> data = Map.ofEntries(
                Map.entry("dealerCount", boughtFrom.size()),
                Map.entry("ticketCount", snapshot.ticketCount()),
                Map.entry("highestReturnDealerId", highest.dealerId()),
                Map.entry("highestReturnDealerName", highest.dealerName()),
                Map.entry("highestReturnRate", highest.returnRate()),
                Map.entry("lowestReturnDealerId", lowest.dealerId()),
                Map.entry("lowestReturnDealerName", lowest.dealerName()),
                Map.entry("lowestReturnRate", lowest.returnRate()),
                Map.entry("returnRateSpread", spread));

        String message = "Your return at %s was %s, at %s it was %s. "
                .formatted(highest.dealerName(), percent(highest.returnRate()),
                        lowest.dealerName(), percent(lowest.returnRate()))
                + "Both stores sold books with the same payout ratio — "
                + "the difference was variance, not the store.";

        return Optional.of(new Insight(TYPE, InsightSeverity.INFO, TITLE, message, data, clock.instant()));
    }

    private static boolean wasBoughtFrom(DealerStats dealer) {
        return dealer.ticketsBought() > 0 && dealer.totalSpent().compareTo(BigDecimal.ZERO) > 0;
    }

    private static String percent(BigDecimal returnRate) {
        return returnRate.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
