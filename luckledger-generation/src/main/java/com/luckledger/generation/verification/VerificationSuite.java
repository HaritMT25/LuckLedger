package com.luckledger.generation.verification;

import com.luckledger.domain.generation.TicketLayout;
import com.luckledger.domain.generation.verification.CheckResult;
import com.luckledger.domain.generation.verification.NearMissReport;
import com.luckledger.domain.generation.verification.VerificationReport;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.NearMissResult;
import com.luckledger.domain.pool.PoolContract;
import com.luckledger.domain.pool.PrizeTier;
import com.luckledger.domain.pool.ValidationResult;
import com.luckledger.domain.pool.PoolValidator;
import com.luckledger.mechanic.NearMissAnalyzer;
import com.luckledger.mechanic.WinEvaluator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The mandatory verification gate (Subsystem 5): runs a generated batch through a battery of checks
 * and reports whether it may ship. Generation aborts if the report does not pass — a failed pool is
 * never patched (the "verification mandatory" invariant).
 *
 * <p>Each layout is evaluated once with the supplied {@link WinEvaluator}; the resulting prizes drive
 * every check: the pool contract is internally sound, the realized per-tier counts and overall payout
 * ratio match the pool, no loser evaluates as a winner and no winner is malformed, and winners are not
 * clumped (a chi-squared spread test). Near-miss analysis is reported separately as information.
 */
public final class VerificationSuite {

    /** Number of positional segments the spread check buckets winners into. */
    private static final int SEGMENTS = 4;
    /** Chi-squared critical value, df = SEGMENTS-1 = 3, alpha = 0.05. */
    private static final double CHI_SQUARED_CRITICAL = 7.815;
    /** Chi-squared needs an adequate expected count per bucket to be meaningful. */
    private static final int MIN_EXPECTED_PER_SEGMENT = 5;
    /** Allowed deviation of the realized payout ratio from the pool's target. */
    private static final BigDecimal PAYOUT_TOLERANCE = new BigDecimal("0.005");

    private final PoolValidator poolValidator;
    private final NearMissAnalyzer nearMissAnalyzer;

    public VerificationSuite(PoolValidator poolValidator, NearMissAnalyzer nearMissAnalyzer) {
        this.poolValidator = Objects.requireNonNull(poolValidator, "poolValidator must not be null");
        this.nearMissAnalyzer = Objects.requireNonNull(nearMissAnalyzer, "nearMissAnalyzer must not be null");
    }

    /**
     * Runs every check against the generated batch.
     *
     * @param layouts the generated layouts, in their shipped (shuffled) order; never {@code null}
     * @param pool the contract the batch was generated from; never {@code null}
     * @param evaluator the mechanic's evaluator, used to read each layout's prize; never {@code null}
     * @return a {@link VerificationReport}; {@link VerificationReport#passed()} is {@code true} only if
     *     every check passed
     */
    public VerificationReport verify(
            List<TicketLayout> layouts, PoolContract pool, WinEvaluator evaluator) {
        Objects.requireNonNull(layouts, "layouts must not be null");
        Objects.requireNonNull(pool, "pool must not be null");
        Objects.requireNonNull(evaluator, "evaluator must not be null");

        List<EvaluationResult> results = new ArrayList<>(layouts.size());
        for (TicketLayout layout : layouts) {
            results.add(evaluator.evaluate(layout.grid()));
        }

        List<CheckResult> checks = new ArrayList<>();
        checks.add(checkPoolContract(pool));
        checks.add(checkTierCounts(results, pool));
        checks.add(checkPayoutRatio(results, pool));
        checks.add(checkNoFalsePositives(results, pool));
        checks.add(checkNoBrokenWinners(results, pool));
        checks.add(checkDistributionSpread(results, pool));
        return VerificationReport.from(checks);
    }

    /**
     * Summarizes how close the losing tickets came to winning. Informational only — never a
     * pass/fail signal.
     *
     * @param layouts the generated layouts; never {@code null}
     * @param evaluator the mechanic's evaluator; never {@code null}
     * @return the near-miss summary across all losers
     */
    public NearMissReport analyzeNearMisses(List<TicketLayout> layouts, WinEvaluator evaluator) {
        Objects.requireNonNull(layouts, "layouts must not be null");
        Objects.requireNonNull(evaluator, "evaluator must not be null");

        int totalLosers = 0;
        int nearMissCount = 0;
        Map<Integer, Integer> distribution = new TreeMap<>();
        for (TicketLayout layout : layouts) {
            EvaluationResult result = evaluator.evaluate(layout.grid());
            if (result.isWinner()) {
                continue;
            }
            totalLosers++;
            NearMissResult nearMiss;
            try {
                nearMiss = nearMissAnalyzer.analyze(result, layout.mechanicType());
            } catch (IllegalArgumentException unsupportedMechanic) {
                // The analyzer only handles match-count mechanics; for others (e.g. point
                // accumulation) we still count the loser but have no near-miss signal to report.
                continue;
            }
            if (nearMiss.isNearMiss()) {
                nearMissCount++;
            }
            distribution.merge(nearMiss.distance(), 1, Integer::sum);
        }
        double rate = totalLosers == 0 ? 0.0 : (double) nearMissCount / totalLosers;
        return new NearMissReport(totalLosers, nearMissCount, rate, distribution);
    }

    private CheckResult checkPoolContract(PoolContract pool) {
        ValidationResult result = poolValidator.validate(pool);
        return result.isValid()
                ? CheckResult.passed("Pool Contract")
                : CheckResult.failed("Pool Contract", String.join("; ", result.errors()));
    }

    private CheckResult checkTierCounts(List<EvaluationResult> results, PoolContract pool) {
        if (results.size() != pool.totalTickets()) {
            return CheckResult.failed(
                    "Tier Counts",
                    "expected " + pool.totalTickets() + " tickets, got " + results.size());
        }
        for (PrizeTier tier : pool.prizeTiers()) {
            long actual = results.stream().filter(r -> r.prizeAmount().compareTo(tier.value()) == 0).count();
            if (actual != tier.count()) {
                return CheckResult.failed(
                        "Tier Counts",
                        "tier $" + tier.value().toPlainString() + " expected " + tier.count() + ", got " + actual);
            }
        }
        long losers = results.stream().filter(r -> isLoser(r, pool)).count();
        if (losers != pool.getLoserCount()) {
            return CheckResult.failed(
                    "Tier Counts", "expected " + pool.getLoserCount() + " losers, got " + losers);
        }
        return CheckResult.passed("Tier Counts");
    }

    private CheckResult checkPayoutRatio(List<EvaluationResult> results, PoolContract pool) {
        BigDecimal paid = results.stream()
                .map(EvaluationResult::prizeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal revenue = pool.getTotalRevenue();
        if (revenue.signum() == 0) {
            return CheckResult.failed("Payout Ratio", "pool revenue is zero");
        }
        BigDecimal actual = paid.divide(revenue, 6, RoundingMode.HALF_UP);
        BigDecimal delta = actual.subtract(pool.payoutRatio()).abs();
        return delta.compareTo(PAYOUT_TOLERANCE) <= 0
                ? CheckResult.passed("Payout Ratio")
                : CheckResult.failed(
                        "Payout Ratio",
                        "realized " + actual.toPlainString() + " vs target " + pool.payoutRatio().toPlainString());
    }

    private CheckResult checkNoFalsePositives(List<EvaluationResult> results, PoolContract pool) {
        for (int i = 0; i < results.size(); i++) {
            EvaluationResult r = results.get(i);
            if (isLoser(r, pool) && r.isWinner()) {
                return CheckResult.failed(
                        "No False Positives", "loser at index " + i + " evaluated as a winner");
            }
        }
        return CheckResult.passed("No False Positives");
    }

    private CheckResult checkNoBrokenWinners(List<EvaluationResult> results, PoolContract pool) {
        for (int i = 0; i < results.size(); i++) {
            EvaluationResult r = results.get(i);
            boolean paysAboveFloor = r.prizeAmount().compareTo(pool.minPayout()) > 0;
            if (paysAboveFloor && !r.isWinner()) {
                return CheckResult.failed(
                        "No Broken Winners", "winner at index " + i + " reported isWinner=false");
            }
            if (r.isWinner() && !isPoolPrize(r.prizeAmount(), pool)) {
                return CheckResult.failed(
                        "No Broken Winners",
                        "winner at index " + i + " pays $" + r.prizeAmount().toPlainString() + ", not a pool tier");
            }
        }
        return CheckResult.passed("No Broken Winners");
    }

    private CheckResult checkDistributionSpread(List<EvaluationResult> results, PoolContract pool) {
        long winners = results.stream().filter(r -> !isLoser(r, pool)).count();
        double expectedPerSegment = (double) winners / SEGMENTS;
        if (expectedPerSegment < MIN_EXPECTED_PER_SEGMENT) {
            // Too few winners for a meaningful chi-squared test; not enough evidence to flag clumping.
            return CheckResult.passed("Distribution Spread");
        }
        long[] observed = new long[SEGMENTS];
        int n = results.size();
        for (int i = 0; i < n; i++) {
            if (!isLoser(results.get(i), pool)) {
                int segment = Math.min(SEGMENTS - 1, i * SEGMENTS / n);
                observed[segment]++;
            }
        }
        double chiSquared = 0.0;
        for (long o : observed) {
            double diff = o - expectedPerSegment;
            chiSquared += diff * diff / expectedPerSegment;
        }
        return chiSquared <= CHI_SQUARED_CRITICAL
                ? CheckResult.passed("Distribution Spread")
                : CheckResult.failed(
                        "Distribution Spread",
                        "winners clumped: chi-squared " + String.format("%.2f", chiSquared)
                                + " exceeds " + CHI_SQUARED_CRITICAL);
    }

    private static boolean isLoser(EvaluationResult result, PoolContract pool) {
        return result.prizeAmount().compareTo(pool.minPayout()) <= 0;
    }

    private static boolean isPoolPrize(BigDecimal prize, PoolContract pool) {
        return pool.prizeTiers().stream().anyMatch(tier -> tier.value().compareTo(prize) == 0);
    }
}
