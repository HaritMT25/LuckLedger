package com.luckledger.api;

import com.luckledger.cli.GameOrchestrator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Populates the {@link GameStore} at startup with the two demo games (Celestial Fortune and Demon
 * Seal), each generated, verified, partitioned, and allocated via its orchestrator. There is no live
 * game-creation endpoint, so this is how games come into existence.
 */
@Component
public class GameSeeder implements ApplicationRunner {

    private final GameOrchestrator celestialOrchestrator;
    private final GameOrchestrator demonOrchestrator;
    private final GameStore gameStore;

    public GameSeeder(
            @Qualifier("celestialOrchestrator") GameOrchestrator celestialOrchestrator,
            @Qualifier("demonOrchestrator") GameOrchestrator demonOrchestrator,
            GameStore gameStore) {
        this.celestialOrchestrator = celestialOrchestrator;
        this.demonOrchestrator = demonOrchestrator;
        this.gameStore = gameStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        gameStore.register(celestialOrchestrator.setup(ApiConfig.celestialConfig()));
        gameStore.register(demonOrchestrator.setup(ApiConfig.demonConfig()));
    }
}
