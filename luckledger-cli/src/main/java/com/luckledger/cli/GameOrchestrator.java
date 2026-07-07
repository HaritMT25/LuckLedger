package com.luckledger.cli;

import com.luckledger.distribution.BookPartitioner;
import com.luckledger.distribution.Dealer;
import com.luckledger.distribution.DealerAllocator;
import com.luckledger.distribution.DealerRegistry;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.distribution.PartitionResult;
import com.luckledger.distribution.TicketBook;
import com.luckledger.domain.generation.GenerationResult;
import com.luckledger.domain.generation.theme.ThemeRef;
import com.luckledger.domain.orchestration.GameConfig;
import com.luckledger.generation.pipeline.GenerationPipeline;
import com.luckledger.generation.theme.ThemeSkinningService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Subsystem 12: the thin top-level flow that turns a {@link GameConfig} into a ready-to-play game —
 * generate a verified batch, partition it into books, and allocate them to ranked dealers. Every step
 * is delegated to an injected service (Dependency Inversion); the orchestrator only wires them.
 *
 * <p>Design reconciliations (see DESIGN vs. the merged code): the config already carries a validated
 * {@code PoolContract}, so {@code PoolFactory} is bypassed; the pipeline needs a {@link ThemeRef}, so
 * a {@link ThemeSkinningService} resolves {@code config.themeId()}; and {@code BookPartitioner} needs
 * a pool id that {@code PoolContract} does not carry, so one is minted per run.
 */
public final class GameOrchestrator {

    private final GenerationPipeline generationPipeline;
    private final BookPartitioner bookPartitioner;
    private final DealerAllocator dealerAllocator;
    private final DealerRegistry dealerRegistry;
    private final ThemeSkinningService themeSkinningService;

    public GameOrchestrator(
            GenerationPipeline generationPipeline,
            BookPartitioner bookPartitioner,
            DealerAllocator dealerAllocator,
            DealerRegistry dealerRegistry,
            ThemeSkinningService themeSkinningService) {
        this.generationPipeline = Objects.requireNonNull(generationPipeline, "generationPipeline must not be null");
        this.bookPartitioner = Objects.requireNonNull(bookPartitioner, "bookPartitioner must not be null");
        this.dealerAllocator = Objects.requireNonNull(dealerAllocator, "dealerAllocator must not be null");
        this.dealerRegistry = Objects.requireNonNull(dealerRegistry, "dealerRegistry must not be null");
        this.themeSkinningService = Objects.requireNonNull(themeSkinningService, "themeSkinningService must not be null");
    }

    /**
     * Sets up a brand-new game: generates, partitions, creates fresh dealers, and allocates.
     *
     * @param config the game configuration; never {@code null}
     * @return the full setup result
     */
    public GameSetupResult setup(GameConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        List<Dealer> dealers = dealerRegistry.initializeDealers(config.dealerCount());
        return runCycle(config, dealers);
    }

    /**
     * Restocks an existing game: generates a fresh batch and allocates it to the <em>existing</em>
     * dealers, so their ranks (accumulated from prior cycles) persist and shape the new allocation.
     *
     * @param config the game configuration; never {@code null}
     * @return the setup result for the new cycle
     */
    public GameSetupResult restockCycle(GameConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        return runCycle(config, dealerRegistry.getAllDealers());
    }

    private GameSetupResult runCycle(GameConfig config, List<Dealer> dealers) {
        ThemeRef theme = themeSkinningService.getTheme(config.themeId());
        GenerationResult generation = generationPipeline.generate(
                config.poolContract(), config.mechanicType(), theme, config.nearMissMode());

        UUID poolContractId = UUID.randomUUID(); // PoolContract carries no id; mint one per cycle
        PartitionResult partition =
                bookPartitioner.partition(generation.tickets(), config.bookCount(), poolContractId);

        Map<Dealer, List<TicketBook>> allocation = dealerAllocator.allocate(partition.books(), dealers);
        return new GameSetupResult(dealers, generation, partition, allocation);
    }
}
