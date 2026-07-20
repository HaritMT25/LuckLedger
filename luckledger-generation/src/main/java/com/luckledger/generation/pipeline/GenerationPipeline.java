package com.luckledger.generation.pipeline;

import com.luckledger.domain.generation.GenerationIntegrityException;
import com.luckledger.domain.generation.GenerationResult;
import com.luckledger.domain.generation.NearMissMode;
import com.luckledger.domain.generation.OutcomeGenerator;
import com.luckledger.domain.generation.ShuffleService;
import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.generation.TicketLayout;
import com.luckledger.domain.generation.TicketOutcome;
import com.luckledger.domain.generation.theme.ThemeRef;
import com.luckledger.domain.generation.verification.NearMissReport;
import com.luckledger.domain.generation.verification.VerificationReport;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridPopulator;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.LoserLayout;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.pool.PoolContract;
import com.luckledger.generation.theme.ThemeSkinningService;
import com.luckledger.generation.verification.VerificationSuite;
import com.luckledger.mechanic.GameMechanic;
import com.luckledger.mechanic.WinEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Subsystem 4: composes Layers 2–4 of the generation pipeline into one orchestrated flow —
 * outcomes → shuffle → populate → <strong>verify</strong> → skin → assemble. Every collaborator is
 * injected as an interface or service (Dependency Inversion); the pipeline never constructs them.
 *
 * <p>If the mandatory verification pass fails, the whole batch is rejected with a
 * {@link GenerationIntegrityException} — a generated pool is never patched or partially shipped.
 *
 * <p>The mechanic and its grid size are fixed per pipeline instance (a pipeline generates one
 * mechanic at one size); {@link #generate(com.luckledger.domain.pool.PoolContract, MechanicType,
 * ThemeRef)} takes the {@link MechanicType} as well so it can guard against a caller passing a type
 * the pipeline was not configured for.
 */
public final class GenerationPipeline {

    /**
     * Fraction of a pool's zero-prize ({@code $0}) losing tickets constructed as engineered
     * near-misses under {@link NearMissMode#REALISTIC}. Set to {@code 0.35}, within DESIGN 3.11's
     * 30-40% band for how heavily a commercial scratch card is tuned to tease a win. This is a layout
     * dial only: it never changes which tickets lose, how many lose, or any prize — the pool's tier
     * counts and total payout (and therefore its RTP) are identical whichever {@link NearMissMode} is
     * used.
     */
    public static final double ENGINEERED_NEAR_MISS_RATE = 0.35;

    private final OutcomeGenerator outcomeGenerator;
    private final ShuffleService shuffleService;
    private final GameMechanic mechanic;
    private final ThemeSkinningService themeSkinningService;
    private final VerificationSuite verificationSuite;
    private final GridSize gridSize;

    public GenerationPipeline(
            OutcomeGenerator outcomeGenerator,
            ShuffleService shuffleService,
            GameMechanic mechanic,
            ThemeSkinningService themeSkinningService,
            VerificationSuite verificationSuite,
            GridSize gridSize) {
        this.outcomeGenerator = Objects.requireNonNull(outcomeGenerator, "outcomeGenerator must not be null");
        this.shuffleService = Objects.requireNonNull(shuffleService, "shuffleService must not be null");
        this.mechanic = Objects.requireNonNull(mechanic, "mechanic must not be null");
        this.themeSkinningService = Objects.requireNonNull(themeSkinningService, "themeSkinningService must not be null");
        this.verificationSuite = Objects.requireNonNull(verificationSuite, "verificationSuite must not be null");
        this.gridSize = Objects.requireNonNull(gridSize, "gridSize must not be null");
    }

    /**
     * Generates a fully verified, renderable batch of tickets for the pool, with no engineered
     * near-misses ({@link NearMissMode#CLEAN}).
     *
     * <p>Backwards-compatible overload retained for callers that predate near-miss engineering.
     *
     * @param pool the pool specification; never {@code null}
     * @param mechanicType the mechanic to generate for; must equal the injected mechanic's type
     * @param theme the theme to skin tickets with; never {@code null}, its symbol map must cover the
     *     mechanic's symbols
     * @return the generated batch with its verification and near-miss reports
     * @throws GenerationIntegrityException if the batch fails verification
     * @throws IllegalArgumentException if {@code mechanicType} is not the one this pipeline serves
     */
    public GenerationResult generate(PoolContract pool, MechanicType mechanicType, ThemeRef theme) {
        return generate(pool, mechanicType, theme, NearMissMode.CLEAN);
    }

    /**
     * Generates a fully verified, renderable batch of tickets for the pool.
     *
     * <p>Under {@link NearMissMode#REALISTIC} exactly {@code round(}{@value #ENGINEERED_NEAR_MISS_RATE}
     * {@code * zeroLoserCount)} of the pool's zero-prize ({@code $0}) losing tickets are populated with
     * {@link LoserLayout#NEAR_MISS}, <em>evenly spread across the shipped (shuffled) sale order</em>
     * rather than drawn from its prefix. This matters because the sale order is final —
     * {@code BookPartitioner} deals contiguous chunks of it and never re-shuffles — so a prefix
     * selection would pile every engineered near-miss into the earliest-dealt books; the even spread
     * gives each book its proportional share. Only {@code $0} tickets are eligible: a positive prize,
     * including a pool's positive minimum-payout floor, takes the populator's winner path and is never
     * reshaped, so the quota is computed over {@code $0} tickets alone and is {@code 0} for a pool that
     * has none. Winners and the set of losers are untouched, so every prize and tier count (and
     * therefore the RTP) is identical to {@link NearMissMode#CLEAN}; the mandatory verification gate is
     * applied unchanged, and the returned {@link NearMissReport} reflects the true engineered near-miss
     * rate.
     *
     * @param pool the pool specification; never {@code null}
     * @param mechanicType the mechanic to generate for; must equal the injected mechanic's type
     * @param theme the theme to skin tickets with; never {@code null}, its symbol map must cover the
     *     mechanic's symbols
     * @param nearMissMode whether to engineer near-misses into losers; never {@code null}
     * @return the generated batch with its verification and near-miss reports
     * @throws GenerationIntegrityException if the batch fails verification
     * @throws IllegalArgumentException if {@code mechanicType} is not the one this pipeline serves
     */
    public GenerationResult generate(
            PoolContract pool, MechanicType mechanicType, ThemeRef theme, NearMissMode nearMissMode) {
        Objects.requireNonNull(pool, "pool must not be null");
        Objects.requireNonNull(mechanicType, "mechanicType must not be null");
        Objects.requireNonNull(theme, "theme must not be null");
        Objects.requireNonNull(nearMissMode, "nearMissMode must not be null");
        if (mechanicType != mechanic.getType()) {
            throw new IllegalArgumentException(
                    "pipeline is configured for " + mechanic.getType() + ", not " + mechanicType);
        }
        long startNanos = System.nanoTime();

        List<TicketOutcome> outcomes = shuffleService.shuffle(outcomeGenerator.generate(pool));

        // Only $0 tickets can be reshaped into near-misses; a positive prize — including a pool's
        // positive minimum-payout floor — takes the populator's winner path and ignores LoserLayout.
        // So the quota (and its spread) is computed over $0 tickets alone, and is 0 for a pool without
        // any. loserIndex walks those $0 tickets in shipped order; the Bresenham selector then flags
        // exactly `engineeredNearMisses` of them, spread evenly across the whole sequence rather than
        // its prefix.
        int engineerableLosers = countEngineerableLosers(outcomes);
        int engineeredNearMisses = nearMissMode == NearMissMode.REALISTIC
                ? (int) Math.round(ENGINEERED_NEAR_MISS_RATE * engineerableLosers)
                : 0;

        GridPopulator populator = mechanic.createPopulator();
        List<String> symbolPool = mechanic.getDefaultSymbolPool();
        List<TicketLayout> layouts = new ArrayList<>(outcomes.size());
        int loserIndex = 0;
        for (TicketOutcome outcome : outcomes) {
            LoserLayout layout = LoserLayout.CLEAN;
            if (isEngineerableLoser(outcome)) {
                if (selectedAsNearMiss(loserIndex, engineeredNearMisses, engineerableLosers)) {
                    layout = LoserLayout.NEAR_MISS;
                }
                loserIndex++;
            }
            Grid grid =
                    populator.populate(gridSize, outcome.prizeAmount().doubleValue(), symbolPool, layout);
            layouts.add(new TicketLayout(outcome.outcomeId(), grid, mechanicType, outcome.prizeAmount()));
        }

        WinEvaluator evaluator = mechanic.createEvaluator();
        VerificationReport report = verificationSuite.verify(layouts, pool, evaluator);
        if (!report.passed()) {
            throw new GenerationIntegrityException(
                    "generated batch failed verification; aborting (no patching)", report);
        }
        NearMissReport nearMissReport = verificationSuite.analyzeNearMisses(layouts, evaluator);

        List<TicketCard> tickets = new ArrayList<>(layouts.size());
        for (TicketLayout layout : layouts) {
            tickets.add(themeSkinningService.skin(layout, theme));
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        return new GenerationResult(tickets, report, nearMissReport, elapsedMs);
    }

    /** Counts the tickets eligible for engineered near-misses — those paying exactly {@code $0}. */
    private static int countEngineerableLosers(List<TicketOutcome> outcomes) {
        int count = 0;
        for (TicketOutcome outcome : outcomes) {
            if (isEngineerableLoser(outcome)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether a ticket is eligible to be reshaped into an engineered near-miss. Only a {@code $0}
     * ticket qualifies: the populators reshape a losing grid only when its prize is exactly zero, so a
     * positive prize — including a pool's positive minimum-payout floor — takes the winner path and
     * ignores {@link LoserLayout}. Basing eligibility on {@code $0} keeps the pipeline's notion of a
     * "loser" in step with the populators, so the engineered quota is never spent flagging tickets
     * that cannot actually be reshaped (which would silently make {@link NearMissMode#REALISTIC} a
     * no-op for a positive-floor pool).
     */
    private static boolean isEngineerableLoser(TicketOutcome outcome) {
        return outcome.prizeAmount().signum() == 0;
    }

    /**
     * Even-spread selector deciding whether the loser at {@code loserIndex} — its 0-based position
     * among the {@code total} engineerable losers, in shipped (shuffled) order — is one of the
     * {@code engineered} near-misses.
     *
     * <p>Uses an exact Bresenham stride: the loser is flagged iff
     * {@code floor((loserIndex + 1) * engineered / total) > floor(loserIndex * engineered / total)}.
     * Summed over {@code loserIndex = 0..total-1} this telescopes to exactly {@code engineered} flags,
     * and it distributes them as evenly as integer arithmetic permits — any contiguous run of
     * {@code w} losers receives either {@code floor(w * engineered / total)} or
     * {@code ceil(w * engineered / total)} of them. Because {@code BookPartitioner} deals contiguous
     * chunks of this exact order and never re-shuffles, no book can be handed every engineered
     * near-miss while another gets none (the prior prefix selection did exactly that). {@code long}
     * arithmetic keeps the products exact for large pools.
     *
     * @param loserIndex 0-based index of this loser among engineerable losers
     *     ({@code 0 <= loserIndex < total})
     * @param engineered number of near-misses to distribute ({@code 0 <= engineered <= total})
     * @param total number of engineerable losers; always {@code > 0} when this is reached
     * @return {@code true} if this loser should be built as a {@link LoserLayout#NEAR_MISS}
     */
    private static boolean selectedAsNearMiss(int loserIndex, int engineered, int total) {
        long flaggedThroughHere = (long) (loserIndex + 1) * engineered / total;
        long flaggedBeforeHere = (long) loserIndex * engineered / total;
        return flaggedThroughHere > flaggedBeforeHere;
    }
}
