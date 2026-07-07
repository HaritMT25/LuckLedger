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
     * Fraction of a pool's losing tickets constructed as engineered near-misses under
     * {@link NearMissMode#REALISTIC}. Set to {@code 0.35}, within DESIGN 3.11's 30-40% band for how
     * heavily a commercial scratch card is tuned to tease a win. This is a layout dial only: it never
     * changes which tickets lose, how many lose, or any prize — the pool's tier counts and total
     * payout (and therefore its RTP) are identical whichever {@link NearMissMode} is used.
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
     * <p>Under {@link NearMissMode#REALISTIC} the first {@code round(}{@value #ENGINEERED_NEAR_MISS_RATE}
     * {@code * loserCount)} losing tickets — taken in the already-shuffled order, so their board
     * positions stay uniformly spread — are populated with {@link LoserLayout#NEAR_MISS}. Winners and
     * the set of losers are untouched, so every prize and tier count (and therefore the RTP) is
     * identical to {@link NearMissMode#CLEAN}; the mandatory verification gate is applied unchanged,
     * and the returned {@link NearMissReport} reflects the true engineered near-miss rate.
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

        int engineeredNearMisses = nearMissMode == NearMissMode.REALISTIC
                ? (int) Math.round(ENGINEERED_NEAR_MISS_RATE * pool.getLoserCount())
                : 0;

        GridPopulator populator = mechanic.createPopulator();
        List<String> symbolPool = mechanic.getDefaultSymbolPool();
        List<TicketLayout> layouts = new ArrayList<>(outcomes.size());
        int engineeredSoFar = 0;
        for (TicketOutcome outcome : outcomes) {
            LoserLayout layout = LoserLayout.CLEAN;
            if (engineeredSoFar < engineeredNearMisses && isLoser(outcome, pool)) {
                layout = LoserLayout.NEAR_MISS;
                engineeredSoFar++;
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

    /**
     * A losing ticket pays the pool's minimum payout (zero for pure-loser pools) — exactly the value
     * {@code OutcomeGenerator} stamps on losers. Only these tickets are eligible for engineered
     * near-misses; winners are never reshaped.
     */
    private static boolean isLoser(TicketOutcome outcome, PoolContract pool) {
        return outcome.prizeAmount().compareTo(pool.minPayout()) == 0;
    }
}
