package com.luckledger.generation.pipeline;

import com.luckledger.domain.generation.GenerationIntegrityException;
import com.luckledger.domain.generation.GenerationResult;
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
import com.luckledger.domain.mechanic.MechanicType;
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
     * Generates a fully verified, renderable batch of tickets for the pool.
     *
     * @param pool the pool specification; never {@code null}
     * @param mechanicType the mechanic to generate for; must equal the injected mechanic's type
     * @param theme the theme to skin tickets with; never {@code null}, its symbol map must cover the
     *     mechanic's symbols
     * @return the generated batch with its verification and near-miss reports
     * @throws GenerationIntegrityException if the batch fails verification
     * @throws IllegalArgumentException if {@code mechanicType} is not the one this pipeline serves
     */
    public GenerationResult generate(
            com.luckledger.domain.pool.PoolContract pool, MechanicType mechanicType, ThemeRef theme) {
        Objects.requireNonNull(pool, "pool must not be null");
        Objects.requireNonNull(mechanicType, "mechanicType must not be null");
        Objects.requireNonNull(theme, "theme must not be null");
        if (mechanicType != mechanic.getType()) {
            throw new IllegalArgumentException(
                    "pipeline is configured for " + mechanic.getType() + ", not " + mechanicType);
        }
        long startNanos = System.nanoTime();

        List<TicketOutcome> outcomes = shuffleService.shuffle(outcomeGenerator.generate(pool));

        GridPopulator populator = mechanic.createPopulator();
        List<String> symbolPool = mechanic.getDefaultSymbolPool();
        List<TicketLayout> layouts = new ArrayList<>(outcomes.size());
        for (TicketOutcome outcome : outcomes) {
            Grid grid = populator.populate(gridSize, outcome.prizeAmount().doubleValue(), symbolPool);
            layouts.add(new TicketLayout(outcome.outcomeId(), grid, mechanicType));
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
}
