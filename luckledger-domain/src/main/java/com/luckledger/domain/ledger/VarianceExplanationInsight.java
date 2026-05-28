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
 * Educational insight that explains natural inter-book variance once a player has spread their
 * play across several books.
 *
 * <p>Trigger: the player has bought from at least {@value #MIN_BOOKS} distinct books. When it
 * fires, the insight contrasts the highest- and lowest-returning of those books to make the point
 * that books drawn from the same pool — with the same payout ratio — still pay back wildly
 * different amounts purely because of how indivisible big prizes land (see DESIGN §4.2).
 *
 * <p>Severity is {@link InsightSeverity#INFO}: this is a neutral teaching moment, not a warning
 * about harmful play. The generator is stateless; its only collaborator is a {@link Clock} so the
 * produced insight's timestamp is deterministic and testable.
 */
public final class VarianceExplanationInsight implements InsightGenerator {

    /** Minimum number of books a player must have bought from for the insight to fire. */
    static final int MIN_BOOKS = 3;

    private static final String TYPE = "VARIANCE_EXPLANATION";
    private static final String TITLE = "Natural book variance";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Clock clock;

    /**
     * Creates a generator that stamps produced insights using the supplied clock.
     *
     * @param clock source of the insight timestamp; never {@code null}
     */
    public VarianceExplanationInsight(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Produces a variance-explanation insight when the player has bought from at least
     * {@value #MIN_BOOKS} books, contrasting the highest- and lowest-returning of them.
     *
     * <p>Only books the player actually bought from (tickets bought and coins spent both positive)
     * are considered; placeholder entries with no spend are ignored.
     *
     * @param snapshot read-only aggregate of the player's ledger state; never {@code null}
     * @return the insight when at least {@value #MIN_BOOKS} books qualify, otherwise
     *         {@link Optional#empty()}
     */
    @Override
    public Optional<Insight> evaluate(LedgerSnapshot snapshot) {
        List<BookStats> boughtFrom = snapshot.perBookStats().values().stream()
                .filter(VarianceExplanationInsight::wasBoughtFrom)
                .sorted(Comparator.comparing(BookStats::returnRate))
                .toList();

        if (boughtFrom.size() < MIN_BOOKS) {
            return Optional.empty();
        }

        BookStats lowest = boughtFrom.get(0);
        BookStats highest = boughtFrom.get(boughtFrom.size() - 1);

        Map<String, Object> data = Map.of(
                "bookCount", boughtFrom.size(),
                "highestReturnBookId", highest.bookId(),
                "highestReturnRate", highest.returnRate(),
                "lowestReturnBookId", lowest.bookId(),
                "lowestReturnRate", lowest.returnRate());

        String message = "One book returned %s of what you spent on it; another returned just %s. "
                .formatted(percent(highest.returnRate()), percent(lowest.returnRate()))
                + "Both came from the same pool with the same payout ratio — "
                + "this is how natural book-to-book variance works.";

        return Optional.of(new Insight(TYPE, InsightSeverity.INFO, TITLE, message, data, clock.instant()));
    }

    private static boolean wasBoughtFrom(BookStats book) {
        return book.ticketsBought() > 0 && book.totalSpent().compareTo(BigDecimal.ZERO) > 0;
    }

    private static String percent(BigDecimal returnRate) {
        return returnRate.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
