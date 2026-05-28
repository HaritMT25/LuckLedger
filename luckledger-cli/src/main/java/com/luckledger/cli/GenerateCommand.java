package com.luckledger.cli;

import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.orchestration.GameConfig;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The CLI's generation step: run a {@link GameConfig} through the {@link GameOrchestrator} to produce
 * a verified, partitioned, allocated batch, logging a summary. Generation is a build-time step — the
 * resulting {@link GameSetupResult} is handed back to the caller to persist into storage.
 *
 * <p>Output goes through SLF4J (never {@code System.out}).
 */
public final class GenerateCommand {

    private static final Logger log = LoggerFactory.getLogger(GenerateCommand.class);

    private final GameOrchestrator orchestrator;

    public GenerateCommand(GameOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
    }

    /**
     * Generates a batch for the config and logs a summary.
     *
     * @param config the game configuration; never {@code null}
     * @return the generated/allocated game
     */
    public GameSetupResult generate(GameConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        log.info(
                "Generating {} pool: {} tickets, {} books, {} dealers",
                config.mechanicType(),
                config.poolContract().totalTickets(),
                config.bookCount(),
                config.dealerCount());

        GameSetupResult result = orchestrator.setup(config);

        boolean verified = result.generationResult().verificationReport().passed();
        log.info(
                "Generated {} tickets into {} books for {} dealers; verification {}",
                result.generationResult().tickets().size(),
                result.partitionResult().books().size(),
                result.dealers().size(),
                verified ? "PASSED" : "FAILED");
        return result;
    }
}
