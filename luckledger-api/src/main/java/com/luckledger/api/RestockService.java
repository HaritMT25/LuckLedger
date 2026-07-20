package com.luckledger.api;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.GamePersistenceMapper;
import com.luckledger.api.persistence.GamePersistenceMapper.PersistedGame;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.PoolContractDoc;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.cli.GameOrchestrator;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.orchestration.GameConfig;
import com.luckledger.domain.orchestration.GameStatus;
import com.luckledger.domain.pool.PoolContract;
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
 *
 * <p><strong>Contract-driven and RTP-immutable.</strong> A campaign game carries the exact pool
 * contract it was generated from ({@code game.pool_contract}); restock regenerates from that, so a
 * restocked batch has identical economics — the RTP is structurally the same. There is no way to
 * <em>retune</em> a game's economics through restock; retuning means retiring and creating a new
 * campaign. Legacy games with no stored contract fall back to the {@link ApiConfig} seed statics. A
 * retired game cannot be restocked (409).
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

        // A retired campaign is off the shelf: it cannot be restocked (409 CONFLICT).
        if (game.getStatus() == GameStatus.RETIRED) {
            throw new IllegalStateException("game " + gameId + " is retired and cannot be restocked");
        }

        GameOrchestrator orchestrator = orchestratorFor(game.getMechanicType());
        GameConfig config = configFor(game);

        List<DealerEntity> stockingShops = gameStore.dealers().stream()
                .filter(shop -> shop.getStockedGames().contains(gameId))
                .toList();
        if (stockingShops.isEmpty()) {
            throw new IllegalStateException("no shop stocks game " + gameId + "; nothing to restock");
        }

        GameSetupResult setup = orchestrator.setup(config, GameSeeder.dealerSlots(stockingShops));
        // Reuse the seeder's mapper with the EXISTING game id so the new books/tickets attach to this
        // game; the mapped game row itself is discarded — the original keeps its identity and history.
        // New books get the same rotated visibility tiers the seeder uses, for demo variety.
        PersistedGame persisted = GamePersistenceMapper.toPersisted(
                gameId, config, setup, Instant.now(), GameSeeder::rotatedVisibility);
        books.saveAll(persisted.books());
        tickets.saveAll(persisted.tickets());

        game.recordRestock(persisted.tickets().size(), persisted.books().size());
        games.save(game);

        return new RestockResult(gameId, persisted.books().size(), persisted.tickets().size());
    }

    private GameOrchestrator orchestratorFor(com.luckledger.domain.mechanic.MechanicType mechanic) {
        return switch (mechanic) {
            case CELESTIAL_FORTUNE -> celestialOrchestrator;
            case DEMON_SEAL -> demonOrchestrator;
            default -> throw new IllegalStateException("no restock orchestrator for mechanic " + mechanic);
        };
    }

    /**
     * The config to regenerate a batch for this game. Prefers the game's persisted pool contract (a
     * campaign) so the restocked batch has identical economics — RTP is structurally unchanged. The
     * per-cycle book count is recovered from an existing book's size (all a game's books are the same
     * size), so a restock mints the same number of books as the original cycle. Legacy games with no
     * stored contract fall back to the {@link ApiConfig} seed statics.
     */
    private GameConfig configFor(GameEntity game) {
        PoolContractDoc doc = game.getPoolContract();
        if (doc == null) {
            return switch (game.getMechanicType()) {
                case CELESTIAL_FORTUNE -> ApiConfig.celestialConfig();
                case DEMON_SEAL -> ApiConfig.demonConfig();
                default -> throw new IllegalStateException(
                        "no restock config for mechanic " + game.getMechanicType());
            };
        }
        PoolContract pool = doc.toDomain();
        int perBookSize = books.findByGameId(game.getId()).stream()
                .map(b -> b.getTotalTickets())
                .filter(size -> size > 0)
                .findFirst()
                .orElse(pool.totalTickets());
        int bookCount = Math.max(1, pool.totalTickets() / perBookSize);
        int dealerCount = (int) gameStore.dealers().stream()
                .filter(shop -> shop.getStockedGames().contains(game.getId()))
                .count();
        String themeId = switch (game.getMechanicType()) {
            case CELESTIAL_FORTUNE -> ApiConfig.CELESTIAL_THEME_ID;
            case DEMON_SEAL -> ApiConfig.DEMON_THEME_ID;
            default -> game.getThemeId();
        };
        return new GameConfig(
                pool, game.getMechanicType(), themeId, bookCount, Math.max(1, dealerCount),
                game.getNearMissMode());
    }
}
