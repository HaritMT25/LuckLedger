package com.luckledger.api;

import com.luckledger.cli.GameOrchestrator;
import com.luckledger.distribution.GameSetupResult;

/** Builds real seeded games for controller tests, reusing {@link ApiConfig}'s wiring. */
final class TestGames {

    private TestGames() {}

    private static GameOrchestrator demonOrchestrator() {
        ApiConfig config = new ApiConfig();
        return config.demonOrchestrator(
                config.outcomeGenerator(),
                config.shuffleService(),
                config.themeSkinningService(),
                config.verificationSuite(config.poolValidator(), config.nearMissAnalyzer()),
                config.bookPartitioner(),
                config.dealerTierResolver());
    }

    /** A fully set-up Demon Seal game (20 tickets, 3 dealers). */
    static GameSetupResult demonGame() {
        return demonOrchestrator().setup(ApiConfig.demonConfig());
    }
}
