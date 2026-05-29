package com.luckledger.api;

import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.api.persistence.GamePersistenceMapper;
import com.luckledger.api.persistence.GamePersistenceMapper.PersistedGame;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.cli.GameOrchestrator;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.orchestration.GameConfig;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the two demo games (Celestial Fortune and Demon Seal) to Postgres at startup — each
 * generated, verified, partitioned, and allocated via its orchestrator, then flattened to entity rows
 * by {@link GamePersistenceMapper}. There is no live game-creation endpoint, so this is how games come
 * into existence.
 *
 * <p>Idempotent: if any game is already persisted (e.g. a restart against a populated database) it
 * does nothing, so the demo set is never duplicated.
 */
@Component
public class GameSeeder implements ApplicationRunner {

    private final GameOrchestrator celestialOrchestrator;
    private final GameOrchestrator demonOrchestrator;
    private final GameRepository games;
    private final DealerRepository dealers;
    private final TicketBookRepository books;
    private final TicketRepository tickets;

    public GameSeeder(
            @Qualifier("celestialOrchestrator") GameOrchestrator celestialOrchestrator,
            @Qualifier("demonOrchestrator") GameOrchestrator demonOrchestrator,
            GameRepository games,
            DealerRepository dealers,
            TicketBookRepository books,
            TicketRepository tickets) {
        this.celestialOrchestrator = celestialOrchestrator;
        this.demonOrchestrator = demonOrchestrator;
        this.games = games;
        this.dealers = dealers;
        this.books = books;
        this.tickets = tickets;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (games.count() > 0) {
            return; // already seeded
        }
        seed(ApiConfig.celestialConfig(), celestialOrchestrator);
        seed(ApiConfig.demonConfig(), demonOrchestrator);
    }

    private void seed(GameConfig config, GameOrchestrator orchestrator) {
        GameSetupResult setup = orchestrator.setup(config);
        PersistedGame persisted =
                GamePersistenceMapper.toPersisted(UUID.randomUUID(), config, setup, Instant.now());
        games.save(persisted.game());
        dealers.saveAll(persisted.dealers());
        books.saveAll(persisted.books());
        tickets.saveAll(persisted.tickets());
    }
}
