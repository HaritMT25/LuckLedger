package com.luckledger.generation.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.TicketLayout;
import com.luckledger.domain.generation.verification.CheckResult;
import com.luckledger.domain.generation.verification.NearMissReport;
import com.luckledger.domain.generation.verification.VerificationReport;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.pool.BookProfile;
import com.luckledger.domain.pool.PoolContract;
import com.luckledger.domain.pool.PoolValidator;
import com.luckledger.domain.pool.PrizeTier;
import com.luckledger.mechanic.DemonSealEvaluator;
import com.luckledger.mechanic.DemonSealPopulator;
import com.luckledger.mechanic.NearMissAnalyzer;
import com.luckledger.mechanic.WinEvaluator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class VerificationSuiteTest {

    private static final List<String> FILLER = List.of("RUNE", "SKULL", "CHAIN");
    private final DemonSealEvaluator evaluator = new DemonSealEvaluator();
    private final VerificationSuite suite =
            new VerificationSuite(new PoolValidator(), new NearMissAnalyzer());

    /** Builds Demon Seal layouts for the given prize sequence, in order. */
    private static List<TicketLayout> layouts(double... prizes) {
        DemonSealPopulator populator = new DemonSealPopulator(new Random(99L));
        List<TicketLayout> layouts = new ArrayList<>(prizes.length);
        for (double prize : prizes) {
            Grid grid = populator.populate(GridSize.THREE, prize, FILLER);
            layouts.add(new TicketLayout(
                    java.util.UUID.randomUUID(), grid, MechanicType.DEMON_SEAL, BigDecimal.valueOf((long) prize)));
        }
        return layouts;
    }

    private static double[] sequence(double prize, int count) {
        double[] out = new double[count];
        java.util.Arrays.fill(out, prize);
        return out;
    }

    private static CheckResult check(VerificationReport report, String name) {
        return report.checks().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no check named " + name));
    }

    // pool: 10 tickets, 2x$10 + 3x$2 + 5 losers; payoutRatio = 26/50 = 0.52
    private static PoolContract pool() {
        return PoolContract.builder()
                .totalTickets(10)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.52"))
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 2, "Sealed-M"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 3, "Consolation"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .build();
    }

    @Test
    void aWellFormedBatchPassesEveryCheck() {
        List<TicketLayout> batch = layouts(10, 10, 2, 2, 2, 0, 0, 0, 0, 0);

        VerificationReport report = suite.verify(batch, pool(), evaluator);

        assertThat(report.passed()).isTrue();
        assertThat(report.checks()).extracting(CheckResult::name)
                .contains("Pool Contract", "Tier Counts", "Payout Ratio",
                        "No False Positives", "No Broken Winners", "Distribution Spread");
    }

    @Test
    void wrongTierCountsFailsTheTierCheck() {
        // three $10 instead of two
        List<TicketLayout> batch = layouts(10, 10, 10, 2, 2, 0, 0, 0, 0, 0);

        VerificationReport report = suite.verify(batch, pool(), evaluator);

        assertThat(report.passed()).isFalse();
        assertThat(check(report, "Tier Counts").passed()).isFalse();
    }

    @Test
    void aLoserEvaluatingAsWinnerFailsNoFalsePositives() {
        List<TicketLayout> batch = layouts(10, 10, 2, 2, 2, 0, 0, 0, 0, 0);
        // Evaluator that lies: any $0 result is reported as a winner.
        WinEvaluator lying = grid -> {
            EvaluationResult real = evaluator.evaluate(grid);
            if (real.prizeAmount().signum() == 0) {
                return new EvaluationResult(true, BigDecimal.ZERO, real.winningPositions(), real.matchDetails());
            }
            return real;
        };

        VerificationReport report = suite.verify(batch, pool(), lying);

        assertThat(report.passed()).isFalse();
        assertThat(check(report, "No False Positives").passed()).isFalse();
    }

    @Test
    void clumpedWinnersFailTheDistributionSpread() {
        // 20 winners all at the front, then 20 losers — heavily clumped in the first quartile.
        double[] prizes = new double[40];
        java.util.Arrays.fill(prizes, 0, 20, 2.0);
        java.util.Arrays.fill(prizes, 20, 40, 0.0);
        List<TicketLayout> batch = layouts(prizes);
        PoolContract clumpPool = PoolContract.builder()
                .totalTickets(40)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.20"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 20, "Consolation"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .build();

        VerificationReport report = suite.verify(batch, clumpPool, evaluator);

        assertThat(check(report, "Distribution Spread").passed()).isFalse();
    }

    @Test
    void analyzeNearMissesCountsLosersAndDegradesForUnsupportedMechanics() {
        // Demon Seal is point-accumulation; NearMissAnalyzer only handles match-count mechanics, so
        // losers are counted but no near-miss signal is produced (graceful degradation).
        List<TicketLayout> batch = layouts(10, 2, 0, 0, 0, 0);

        NearMissReport report = suite.analyzeNearMisses(batch, evaluator);

        assertThat(report.totalLosers()).isEqualTo(4); // four $0 tickets
        assertThat(report.nearMissCount()).isZero();
        assertThat(report.nearMissRate()).isZero();
        assertThat(report.distribution()).isEmpty();
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThatThrownBy(() -> suite.verify(null, pool(), evaluator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerificationSuite(null, new NearMissAnalyzer()))
                .isInstanceOf(NullPointerException.class);
    }
}
