package com.luckledger.api;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.GamePersistenceMapper;
import com.luckledger.api.persistence.GamePersistenceMapper.PersistedGame;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.cli.GameOrchestrator;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.orchestration.GameConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The master's restock lever: generates a fresh batch for an existing game — same pool contract,
 * full generation pipeline with mandatory verification — and allocates the new books to the shops
 * already stocking that game (their accumulated ranks shape the allocation, as on any cycle). The
 * existing game row keeps its identity; only its ticket/book totals grow. Nothing about a restock
 * touches sold tickets, the ledger, or the payout ratio.
 */
@Service
public class RestockService {

    private final GameOrchestrator celestialOrchestrator;
    private final GameOrchestrator demonOrchestrator;
    private final GameStore gameStore;
    private final GameRepository games;
    private final TicketBookRepository books;
    private final TicketRepository tickets;

    public RestockService(
            @Qualifier("celestialOrchestrator") GameOrchestrator celestialOrchestrator,
            @Qualifier("demonOrchestrator") GameOrchestrator demonOrchestrator,
            GameStore gameStore, GameRepository games, TicketBookRepository books, TicketRepository tickets) {
        this.celestialOrchestrator = celestialOrchestrator;
        this.demonOrchestrator = demonOrchestrator;
        this.gameStore = gameStore;
        this.games = games;
        this.books = books;
        this.tickets = tickets;
    }

    /** The books/tickets added by one restock cycle. */
    public record RestockResult(UUID gameId, int booksAdded, int ticketsAdded) {}

    @Transactional
    public RestockResult restock(UUID gameId) {
        GameEntity game = gameStore.game(gameId); // 404 if unknown

        GameConfig config;
        GameOrchestrator orchestrator;
        switch (game.getMechanicType()) {
            case CELESTIAL_FORTUNE -> {
                config = ApiConfig.celestialConfig();
                orchestrator = celestialOrchestrator;
            }
            case DEMON_SEAL -> {
                config = ApiConfig.demonConfig();
                orchestrator = demonOrchestrator;
            }
            default -> throw new IllegalStateException(
                    "no restock config for mechanic " + game.getMechanicType());
        }

        List<DealerEntity> stockingShops = gameStore.dealers().stream()
                .filter(shop -> shop.getStockedGames().contains(gameId))
                .toList();
        if (stockingShops.isEmpty()) {
            throw new IllegalStateException("no shop stocks game " + gameId + "; nothing to restock");
        }

        GameSetupResult setup = orchestrator.setup(config, GameSeeder.dealerSlots(stockingShops));
        // Reuse the seeder's mapper with the EXISTING game id so the new books/tickets attach to this
        // game; the mapped game row itself is discarded — the original keeps its identity and history.
        PersistedGame persisted = GamePersistenceMapper.toPersisted(gameId, config, setup, Instant.now());
        books.saveAll(persisted.books());
        tickets.saveAll(persisted.tickets());

        game.recordRestock(persisted.tickets().size(), persisted.books().size());
        games.save(game);

        return new RestockResult(gameId, persisted.books().size(), persisted.tickets().size());
    }
}
