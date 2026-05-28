package com.luckledger.player.ledger;

import com.luckledger.domain.ledger.BookStats;
import com.luckledger.domain.ledger.CurvePoint;
import com.luckledger.domain.ledger.DealerStats;
import com.luckledger.domain.ledger.Insight;
import com.luckledger.domain.ledger.InsightGenerator;
import com.luckledger.domain.ledger.LedgerSnapshot;
import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read side of Subsystem 11 — turns a player's append-only {@link TransactionRecorder} history into
 * ledger snapshots, educational insights, dealer comparisons, and the inevitability curve.
 *
 * <p>The service holds no state of its own: every method derives its result from the transactions
 * the recorder returns, so it is safe to share. Insight production is open for extension via the
 * injected {@link InsightGenerator} strategies (DESIGN §3.13, §9) — adding an observation never
 * touches this class.
 *
 * <p>DESIGN §11 envisions {@code getSnapshot} reading O(1) running totals off a persisted
 * {@code Player} row. No such collaborator is available to this subsystem (the only injected data
 * source is the {@link TransactionRecorder}), so the hot-path totals are folded from the player's
 * transactions instead. Likewise, transactions carry only a {@code dealerId}; with no dealer
 * registry to resolve names, {@link DealerStats#dealerName()} is populated from that id.
 */
public class LedgerService {

    /** Scale used for the BigDecimal division that yields return rates. */
    private static final int RETURN_RATE_SCALE = 4;

    /**
     * Number of most-recent transactions surfaced on the snapshot for streak/pattern detection.
     * Comfortably covers every generator's detection window (the widest is ten transactions).
     */
    private static final int RECENT_TRANSACTION_LIMIT = 50;

    private final TransactionRecorder recorder;
    private final List<InsightGenerator> insightGenerators;

    /**
     * @param recorder          append-only source of player transactions; never {@code null}
     * @param insightGenerators registered educational observers, run on every snapshot; never
     *                          {@code null}, copied defensively
     * @throws NullPointerException if either argument is {@code null}
     */
    public LedgerService(TransactionRecorder recorder, List<InsightGenerator> insightGenerators) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
        Objects.requireNonNull(insightGenerators, "insightGenerators must not be null");
        this.insightGenerators = List.copyOf(insightGenerators);
    }

    /**
     * Builds a point-in-time aggregate of a player's ledger.
     *
     * @param playerId the player to aggregate; never {@code null}
     * @return the player's snapshot; all totals are zero and all collections empty if the player has
     *     no transactions
     * @throws NullPointerException if {@code playerId} is {@code null}
     */
    public LedgerSnapshot getSnapshot(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        List<Transaction> transactions = recorder.getTransactions(playerId);

        BigDecimal totalBorrowed = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;
        BigDecimal totalWon = BigDecimal.ZERO;
        int ticketCount = 0;

        Map<UUID, Accumulator> byDealer = new LinkedHashMap<>();
        Map<UUID, Accumulator> byBook = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            switch (tx.type()) {
                case BORROW -> totalBorrowed = totalBorrowed.add(tx.amount());
                case SPEND -> {
                    totalSpent = totalSpent.add(tx.amount());
                    ticketCount++;
                    if (tx.dealerId() != null) {
                        byDealer.computeIfAbsent(tx.dealerId(), id -> new Accumulator()).addSpend(tx.amount());
                    }
                    if (tx.bookId() != null) {
                        byBook.computeIfAbsent(tx.bookId(), id -> new Accumulator()).addSpend(tx.amount());
                    }
                }
                case WIN -> {
                    totalWon = totalWon.add(tx.amount());
                    if (tx.dealerId() != null) {
                        byDealer.computeIfAbsent(tx.dealerId(), id -> new Accumulator()).addWin(tx.amount());
                    }
                    if (tx.bookId() != null) {
                        byBook.computeIfAbsent(tx.bookId(), id -> new Accumulator()).addWin(tx.amount());
                    }
                }
            }
        }

        Map<UUID, DealerStats> perDealerStats = new LinkedHashMap<>();
        byDealer.forEach((dealerId, acc) -> perDealerStats.put(dealerId, acc.toDealerStats(dealerId)));

        Map<UUID, BookStats> perBookStats = new LinkedHashMap<>();
        byBook.forEach((bookId, acc) -> perBookStats.put(bookId, acc.toBookStats(bookId)));

        return new LedgerSnapshot(
                totalBorrowed,
                totalSpent,
                totalWon,
                totalWon.subtract(totalSpent),
                ticketCount,
                returnRate(totalWon, totalSpent),
                perDealerStats,
                perBookStats,
                recorder.getRecentTransactions(playerId, RECENT_TRANSACTION_LIMIT),
                recorder.getTransactions(playerId, TransactionType.BORROW));
    }

    /**
     * Runs every registered generator against a fresh snapshot and collects the insights that fired.
     *
     * @param playerId the player to evaluate; never {@code null}
     * @return the insights produced this evaluation, in generator-registration order; empty if none
     *     fired
     * @throws NullPointerException if {@code playerId} is {@code null}
     */
    public List<Insight> generateInsights(UUID playerId) {
        LedgerSnapshot snapshot = getSnapshot(playerId);
        List<Insight> insights = new ArrayList<>();
        for (InsightGenerator generator : insightGenerators) {
            generator.evaluate(snapshot).ifPresent(insights::add);
        }
        return List.copyOf(insights);
    }

    /**
     * Returns the per-dealer drill-down for side-by-side comparison of return rates.
     *
     * @param playerId the player to compare dealers for; never {@code null}
     * @return an unmodifiable map of dealer id to stats; empty if the player bought from no dealer
     * @throws NullPointerException if {@code playerId} is {@code null}
     */
    public Map<UUID, DealerStats> getDealerComparison(UUID playerId) {
        return getSnapshot(playerId).perDealerStats();
    }

    /**
     * Builds the inevitability curve: one point per ticket purchased, in purchase order, carrying
     * cumulative spend, cumulative winnings, and the net position (DESIGN §3.13). Each ticket's
     * winnings are attributed to its purchase point by matching the reveal transaction's ticket id,
     * so a win counts toward the same ticket that paid for it regardless of reveal timing.
     *
     * @param playerId the player to chart; never {@code null}
     * @return the curve in ascending ticket order; empty if the player purchased no tickets
     * @throws NullPointerException if {@code playerId} is {@code null}
     */
    public List<CurvePoint> getInevitabilityCurve(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        List<Transaction> transactions = recorder.getTransactions(playerId);

        Map<UUID, BigDecimal> winByTicket = new LinkedHashMap<>();
        for (Transaction tx : transactions) {
            if (tx.type() == TransactionType.WIN && tx.ticketId() != null) {
                winByTicket.merge(tx.ticketId(), tx.amount(), BigDecimal::add);
            }
        }

        List<CurvePoint> curve = new ArrayList<>();
        BigDecimal cumulativeSpent = BigDecimal.ZERO;
        BigDecimal cumulativeWon = BigDecimal.ZERO;
        int ticketNumber = 0;
        for (Transaction tx : transactions) {
            if (tx.type() != TransactionType.SPEND) {
                continue;
            }
            ticketNumber++;
            cumulativeSpent = cumulativeSpent.add(tx.amount());
            if (tx.ticketId() != null) {
                cumulativeWon = cumulativeWon.add(winByTicket.getOrDefault(tx.ticketId(), BigDecimal.ZERO));
            }
            curve.add(new CurvePoint(
                    ticketNumber, cumulativeSpent, cumulativeWon, cumulativeWon.subtract(cumulativeSpent)));
        }
        return List.copyOf(curve);
    }

    private static BigDecimal returnRate(BigDecimal won, BigDecimal spent) {
        if (spent.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return won.divide(spent, RETURN_RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Mutable spend/win tally for a single dealer or book while a snapshot is being folded. */
    private static final class Accumulator {
        private int ticketsBought;
        private BigDecimal spent = BigDecimal.ZERO;
        private BigDecimal won = BigDecimal.ZERO;

        void addSpend(BigDecimal amount) {
            ticketsBought++;
            spent = spent.add(amount);
        }

        void addWin(BigDecimal amount) {
            won = won.add(amount);
        }

        DealerStats toDealerStats(UUID dealerId) {
            return new DealerStats(dealerId, dealerId.toString(), ticketsBought, spent, won, returnRate(won, spent));
        }

        BookStats toBookStats(UUID bookId) {
            return new BookStats(bookId, ticketsBought, spent, won, returnRate(won, spent));
        }
    }
}
